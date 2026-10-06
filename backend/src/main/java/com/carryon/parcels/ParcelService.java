package com.carryon.parcels;

import com.carryon.common.ApiException;
import com.carryon.common.Tx;
import com.carryon.config.AppProperties;
import com.carryon.notifications.NotificationService;
import com.carryon.parcels.Parcel.Status;
import com.carryon.payments.PaymentService;
import com.carryon.trips.Trip;
import com.carryon.trips.TripOps;
import com.carryon.trips.TripRepository;
import com.carryon.users.User;
import com.carryon.users.UserRepository;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Owns the parcel state machine. Every transition runs in one MongoDB transaction (via Tx, which retries
 * on write conflicts): read the parcel, verify state and actor, mutate, append an audit event. The parcel's
 * @Version makes a concurrent double transition fail and retry, after which the loser sees the new state
 * and gets a clean 409.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParcelService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ParcelRepository parcels;
    private final ParcelEventRepository events;
    private final TripRepository trips;
    private final TripOps tripOps;
    private final UserRepository users;
    private final PaymentService payments;
    private final NotificationService notifications;
    private final AppProperties props;
    private final Tx tx;

    // ---------- sender actions ----------

    public Parcel create(UUID senderId, ParcelController.CreateParcelRequest r) {
        if (!r.noProhibitedItems()) {
            throw ApiException.badRequest("You must confirm the parcel contains no prohibited items");
        }
        if (r.declaredValuePaise() > props.maxDeclaredValuePaise()) {
            throw ApiException.badRequest("Declared value exceeds the platform limit");
        }
        if (r.originCity().trim().equalsIgnoreCase(r.destinationCity().trim())) {
            throw ApiException.badRequest("Origin and destination must differ");
        }
        return tx.run(() -> {
            Parcel p = new Parcel();
            p.setSenderId(senderId);
            p.setDescription(r.description().trim());
            p.setWeightG(r.weightG());
            p.setDeclaredValuePaise(r.declaredValuePaise());
            p.setRewardPaise(r.rewardPaise());
            p.setOriginCity(r.originCity().trim());
            p.setDestinationCity(r.destinationCity().trim());
            p.setRecipientName(r.recipientName().trim());
            p.setRecipientPhone(r.recipientPhone());
            p.setPickupToken(newToken());
            p.setDeliveryToken(newToken());
            parcels.insert(p);
            record(p, "CREATED", senderId, null);
            return p;
        });
    }

    public Parcel requestTrip(UUID senderId, UUID parcelId, UUID tripId) {
        return tx.run(() -> {
            Parcel p = load(parcelId);
            requireSender(p, senderId);
            requireStatus(p, Status.CREATED);
            Trip t = trips.findById(tripId).orElseThrow(() -> ApiException.notFound("Trip"));
            if (t.getStatus() != Trip.Status.OPEN) throw ApiException.conflict("Trip is not open");
            if (t.getTravellerId().equals(senderId)) throw ApiException.badRequest("You cannot carry your own parcel");
            if (!t.getOriginCity().equalsIgnoreCase(p.getOriginCity())
                    || !t.getDestinationCity().equalsIgnoreCase(p.getDestinationCity())) {
                throw ApiException.badRequest("Trip route does not match the parcel route");
            }
            if (t.getDepartureDate().isBefore(LocalDate.now())) throw ApiException.conflict("Trip already departed");
            if (t.availableG() < p.getWeightG()) throw ApiException.conflict("Trip does not have enough spare capacity");
            p.setTripId(tripId);
            p.setStatus(Status.REQUESTED);
            parcels.save(p);
            record(p, "REQUESTED", senderId, "trip " + tripId);
            notifications.notify(t.getTravellerId(), p.getId(), "New parcel request on your " + t.getOriginCity() + " → " + t.getDestinationCity() + " trip");
            return p;
        });
    }

    public Parcel cancel(UUID senderId, UUID parcelId) {
        return tx.run(() -> {
            Parcel p = load(parcelId);
            requireSender(p, senderId);
            requireStatus(p, Status.CREATED, Status.REQUESTED, Status.ACCEPTED);
            UUID tripId = p.getTripId();
            if (p.getStatus() == Status.ACCEPTED) {
                tripOps.release(tripId, p.getWeightG());
                payments.refund(p.getId());
            }
            p.setStatus(Status.CANCELLED);
            parcels.save(p);
            record(p, "CANCELLED", senderId, null);
            if (tripId != null) notifyTraveller(tripId, p, "A parcel on your trip was cancelled");
            return p;
        });
    }

    public Parcel confirmDelivery(UUID senderId, UUID parcelId) {
        return tx.run(() -> {
            Parcel p = load(parcelId);
            requireSender(p, senderId);
            requireStatus(p, Status.DELIVERED);
            return complete(p, senderId, "Sender confirmed delivery");
        });
    }

    // ---------- traveller actions ----------

    public Parcel accept(UUID travellerId, UUID parcelId) {
        return tx.run(() -> {
            Parcel p = load(parcelId);
            Trip t = tripOf(p);
            requireTraveller(t, travellerId);
            requireStatus(p, Status.REQUESTED);
            // Atomic conditional update on the trip document (see TripOps.reserve).
            if (!tripOps.reserve(t.getId(), p.getWeightG())) {
                throw ApiException.conflict("Not enough spare capacity left on this trip");
            }
            payments.hold(p.getId(), p.getSenderId(), p.getRewardPaise());
            p.setStatus(Status.ACCEPTED);
            parcels.save(p);
            record(p, "ACCEPTED", travellerId, null);
            notifications.notify(p.getSenderId(), p.getId(), "Your parcel was accepted. Payment is held in escrow.");
            return p;
        });
    }

    public Parcel reject(UUID travellerId, UUID parcelId) {
        return tx.run(() -> {
            Parcel p = load(parcelId);
            requireTraveller(tripOf(p), travellerId);
            requireStatus(p, Status.REQUESTED);
            p.setTripId(null);
            p.setStatus(Status.CREATED);
            parcels.save(p);
            record(p, "REJECTED", travellerId, null);
            notifications.notify(p.getSenderId(), p.getId(), "A traveller declined your parcel. Pick another trip.");
            return p;
        });
    }

    public Parcel pickup(UUID travellerId, UUID parcelId, String token) {
        return tx.run(() -> {
            Parcel p = load(parcelId);
            requireTraveller(tripOf(p), travellerId);
            requireStatus(p, Status.ACCEPTED);
            requireToken(p.getPickupToken(), token);
            p.setStatus(Status.PICKED_UP);
            parcels.save(p);
            record(p, "PICKED_UP", travellerId, "pickup QR verified");
            notifications.notify(p.getSenderId(), p.getId(), "Your parcel was picked up by the traveller.");
            return p;
        });
    }

    public Parcel deliver(UUID travellerId, UUID parcelId, String token) {
        return tx.run(() -> {
            Parcel p = load(parcelId);
            requireTraveller(tripOf(p), travellerId);
            requireStatus(p, Status.PICKED_UP);
            requireToken(p.getDeliveryToken(), token);
            p.setStatus(Status.DELIVERED);
            p.setDeliveredAt(Instant.now());
            parcels.save(p);
            record(p, "DELIVERED", travellerId, "delivery QR verified");
            notifications.notify(p.getSenderId(), p.getId(),
                    "Parcel delivered. Confirm within " + props.autoReleaseHours() + "h or payment releases automatically.");
            return p;
        });
    }

    // ---------- called by the disputes module ----------

    public void markDisputed(UUID parcelId, UUID actorId) {
        tx.runVoid(() -> {
            Parcel p = load(parcelId);
            requireStatus(p, Status.ACCEPTED, Status.PICKED_UP, Status.DELIVERED);
            p.setStatus(Status.DISPUTED);
            parcels.save(p);
            record(p, "DISPUTED", actorId, null);
        });
    }

    public void settleDispute(UUID parcelId, UUID adminId, boolean refund) {
        tx.runVoid(() -> {
            Parcel p = load(parcelId);
            requireStatus(p, Status.DISPUTED);
            if (refund) {
                payments.refund(p.getId());
                p.setStatus(Status.REFUNDED);
                parcels.save(p);
                record(p, "REFUNDED", adminId, "dispute resolved in favour of sender");
            } else {
                complete(p, adminId, "Dispute resolved in favour of traveller");
            }
            notifications.notify(p.getSenderId(), p.getId(), "Your dispute was resolved: " + (refund ? "refunded" : "payment released to traveller"));
            notifyTraveller(p.getTripId(), p, "A dispute on your parcel was resolved: " + (refund ? "sender refunded" : "payment released to you"));
        });
    }

    // ---------- scheduled ----------

    /** Releases escrow for parcels delivered and left unconfirmed/undisputed past the window. */
    @Scheduled(fixedDelayString = "PT10M")
    public void autoRelease() {
        Instant cutoff = Instant.now().minus(props.autoReleaseHours(), ChronoUnit.HOURS);
        for (Parcel stale : parcels.findByStatusAndDeliveredAtBefore(Status.DELIVERED, cutoff)) {
            try {
                autoReleaseOne(stale.getId());
            } catch (RuntimeException e) {
                log.warn("Auto-release failed for parcel {}: {}", stale.getId(), e.getMessage());
            }
        }
    }

    public void autoReleaseOne(UUID parcelId) {
        tx.runVoid(() -> {
            Parcel p = load(parcelId);
            if (p.getStatus() == Status.DELIVERED) complete(p, null, "Auto-released after confirmation window");
        });
    }

    // ---------- reads ----------

    public Parcel visible(UUID parcelId, UUID viewerId, boolean admin) {
        Parcel p = parcels.findById(parcelId).orElseThrow(() -> ApiException.notFound("Parcel"));
        if (admin || p.getSenderId().equals(viewerId)) return p;
        if (p.getTripId() != null && p.getStatus() != Status.CREATED
                && trips.findById(p.getTripId()).map(t -> t.getTravellerId().equals(viewerId)).orElse(false)) {
            return p;
        }
        throw ApiException.notFound("Parcel");   // 404, not 403: do not leak existence
    }

    public List<Parcel> sent(UUID senderId) { return parcels.findBySenderIdOrderByCreatedAtDesc(senderId); }

    public List<Parcel> assigned(UUID travellerId) {
        List<UUID> tripIds = trips.findByTravellerIdOrderByDepartureDateDesc(travellerId).stream().map(Trip::getId).toList();
        if (tripIds.isEmpty()) return List.of();
        return parcels.findByTripIdInAndStatusNotOrderByCreatedAtDesc(tripIds, Status.CREATED);
    }

    public List<ParcelEvent> timeline(UUID parcelId, UUID viewerId, boolean admin) {
        visible(parcelId, viewerId, admin);
        return events.findByParcelIdOrderByCreatedAtAsc(parcelId);
    }

    /** Builds the API view, hiding secrets from whoever should not see them. */
    public ParcelController.ParcelView view(Parcel p, UUID viewerId, boolean admin) {
        boolean isSender = p.getSenderId().equals(viewerId);
        boolean contactVisible = isSender || admin || !Arrays.asList(Status.REQUESTED, Status.CREATED).contains(p.getStatus());
        UUID travellerId = p.getTripId() == null ? null
                : trips.findById(p.getTripId()).map(Trip::getTravellerId).orElse(null);
        String senderName = users.findById(p.getSenderId()).map(User::getFullName).orElse(null);
        String travellerName = travellerId == null ? null : users.findById(travellerId).map(User::getFullName).orElse(null);
        return new ParcelController.ParcelView(p.getId(), p.getStatus().name(), p.getDescription(), p.getWeightG(),
                p.getDeclaredValuePaise(), p.getRewardPaise(), p.getOriginCity(), p.getDestinationCity(),
                p.getRecipientName(), contactVisible ? p.getRecipientPhone() : null,
                p.getTripId(), senderName, travellerName,
                isSender ? p.getPickupToken() : null, isSender ? p.getDeliveryToken() : null,
                p.getCreatedAt(), p.getDeliveredAt());
    }

    // ---------- internals ----------

    private Parcel complete(Parcel p, UUID actor, String note) {
        payments.release(p.getId());
        p.setStatus(Status.COMPLETED);
        parcels.save(p);
        record(p, "COMPLETED", actor, note);
        if (p.getTripId() != null) notifyTraveller(p.getTripId(), p, "Payment released for a delivered parcel");
        return p;
    }

    private void notifyTraveller(UUID tripId, Parcel p, String msg) {
        if (tripId == null) return;
        trips.findById(tripId).ifPresent(t -> notifications.notify(t.getTravellerId(), p.getId(), msg));
    }

    private Parcel load(UUID id) {
        return parcels.findById(id).orElseThrow(() -> ApiException.notFound("Parcel"));
    }

    private Trip tripOf(Parcel p) {
        if (p.getTripId() == null) throw ApiException.conflict("Parcel is not assigned to a trip");
        return trips.findById(p.getTripId()).orElseThrow(() -> ApiException.notFound("Trip"));
    }

    private void requireSender(Parcel p, UUID userId) {
        if (!p.getSenderId().equals(userId)) throw ApiException.notFound("Parcel");
    }

    private void requireTraveller(Trip t, UUID userId) {
        if (!t.getTravellerId().equals(userId)) throw ApiException.notFound("Parcel");
    }

    private void requireStatus(Parcel p, Status... allowed) {
        if (!Arrays.asList(allowed).contains(p.getStatus())) {
            throw ApiException.conflict("Parcel is " + p.getStatus() + "; action not allowed");
        }
    }

    private void requireToken(String expected, String supplied) {
        boolean ok = supplied != null && MessageDigest.isEqual(expected.getBytes(), supplied.getBytes());
        if (!ok) throw ApiException.badRequest("Invalid QR code");
    }

    private void record(Parcel p, String type, UUID actor, String note) {
        ParcelEvent e = new ParcelEvent();
        e.setParcelId(p.getId());
        e.setType(type);
        e.setActorId(actor);
        e.setNote(note);
        events.insert(e);
    }

    private static String newToken() {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
