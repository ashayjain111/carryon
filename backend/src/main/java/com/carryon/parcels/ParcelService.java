package com.carryon.parcels;

import com.carryon.common.ApiException;
import com.carryon.config.AppProperties;
import com.carryon.notifications.NotificationService;
import com.carryon.parcels.Parcel.Status;
import com.carryon.payments.PaymentService;
import com.carryon.trips.Trip;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the parcel state machine. Every transition: lock the parcel row (SELECT ... FOR UPDATE),
 * verify the current state and the actor, mutate, append an audit event, all in one transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParcelService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ParcelRepository parcels;
    private final ParcelEventRepository events;
    private final TripRepository trips;
    private final UserRepository users;
    private final PaymentService payments;
    private final NotificationService notifications;
    private final AppProperties props;

    // ---------- sender actions ----------

    @Transactional
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
        parcels.save(p);
        record(p, "CREATED", senderId, null);
        return p;
    }

    @Transactional
    public Parcel requestTrip(UUID senderId, UUID parcelId, UUID tripId) {
        Parcel p = lock(parcelId);
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
        record(p, "REQUESTED", senderId, "trip " + tripId);
        notifications.notify(t.getTravellerId(), p.getId(), "New parcel request on your " + t.getOriginCity() + " → " + t.getDestinationCity() + " trip");
        return p;
    }

    @Transactional
    public Parcel cancel(UUID senderId, UUID parcelId) {
        Parcel p = lock(parcelId);
        requireSender(p, senderId);
        requireStatus(p, Status.CREATED, Status.REQUESTED, Status.ACCEPTED);
        UUID tripId = p.getTripId();
        if (p.getStatus() == Status.ACCEPTED) {
            trips.release(tripId, p.getWeightG());
            payments.refund(p.getId());
        }
        p.setStatus(Status.CANCELLED);
        record(p, "CANCELLED", senderId, null);
        if (tripId != null) notifyTraveller(tripId, p, "A parcel on your trip was cancelled");
        return p;
    }

    @Transactional
    public Parcel confirmDelivery(UUID senderId, UUID parcelId) {
        Parcel p = lock(parcelId);
        requireSender(p, senderId);
        requireStatus(p, Status.DELIVERED);
        return complete(p, senderId, "Sender confirmed delivery");
    }

    // ---------- traveller actions ----------

    @Transactional
    public Parcel accept(UUID travellerId, UUID parcelId) {
        Parcel p = lock(parcelId);
        Trip t = tripOf(p);
        requireTraveller(t, travellerId);
        requireStatus(p, Status.REQUESTED);
        // Atomic, race-free capacity reservation (see TripRepository.reserve).
        if (trips.reserve(t.getId(), p.getWeightG()) != 1) {
            throw ApiException.conflict("Not enough spare capacity left on this trip");
        }
        payments.hold(p.getId(), p.getSenderId(), p.getRewardPaise());
        p.setStatus(Status.ACCEPTED);
        record(p, "ACCEPTED", travellerId, null);
        notifications.notify(p.getSenderId(), p.getId(), "Your parcel was accepted. Payment is held in escrow.");
        return p;
    }

    @Transactional
    public Parcel reject(UUID travellerId, UUID parcelId) {
        Parcel p = lock(parcelId);
        requireTraveller(tripOf(p), travellerId);
        requireStatus(p, Status.REQUESTED);
        p.setTripId(null);
        p.setStatus(Status.CREATED);
        record(p, "REJECTED", travellerId, null);
        notifications.notify(p.getSenderId(), p.getId(), "A traveller declined your parcel. Pick another trip.");
        return p;
    }

    @Transactional
    public Parcel pickup(UUID travellerId, UUID parcelId, String token) {
        Parcel p = lock(parcelId);
        requireTraveller(tripOf(p), travellerId);
        requireStatus(p, Status.ACCEPTED);
        requireToken(p.getPickupToken(), token);
        p.setStatus(Status.PICKED_UP);
        record(p, "PICKED_UP", travellerId, "pickup QR verified");
        notifications.notify(p.getSenderId(), p.getId(), "Your parcel was picked up by the traveller.");
        return p;
    }

    @Transactional
    public Parcel deliver(UUID travellerId, UUID parcelId, String token) {
        Parcel p = lock(parcelId);
        requireTraveller(tripOf(p), travellerId);
        requireStatus(p, Status.PICKED_UP);
        requireToken(p.getDeliveryToken(), token);
        p.setStatus(Status.DELIVERED);
        p.setDeliveredAt(Instant.now());
        record(p, "DELIVERED", travellerId, "delivery QR verified");
        notifications.notify(p.getSenderId(), p.getId(),
                "Parcel delivered. Confirm within " + props.autoReleaseHours() + "h or payment releases automatically.");
        return p;
    }

    // ---------- called by the disputes module ----------

    @Transactional
    public void markDisputed(UUID parcelId, UUID actorId) {
        Parcel p = lock(parcelId);
        requireStatus(p, Status.ACCEPTED, Status.PICKED_UP, Status.DELIVERED);
        p.setStatus(Status.DISPUTED);
        record(p, "DISPUTED", actorId, null);
    }

    @Transactional
    public void settleDispute(UUID parcelId, UUID adminId, boolean refund) {
        Parcel p = lock(parcelId);
        requireStatus(p, Status.DISPUTED);
        if (refund) {
            payments.refund(p.getId());
            p.setStatus(Status.REFUNDED);
            record(p, "REFUNDED", adminId, "dispute resolved in favour of sender");
        } else {
            complete(p, adminId, "Dispute resolved in favour of traveller");
        }
        notifications.notify(p.getSenderId(), p.getId(), "Your dispute was resolved: " + (refund ? "refunded" : "payment released to traveller"));
        notifyTraveller(p.getTripId(), p, "A dispute on your parcel was resolved: " + (refund ? "sender refunded" : "payment released to you"));
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

    @Transactional
    public void autoReleaseOne(UUID parcelId) {
        Parcel p = lock(parcelId);
        if (p.getStatus() == Status.DELIVERED) complete(p, null, "Auto-released after confirmation window");
    }

    // ---------- reads ----------

    @Transactional(readOnly = true)
    public Parcel visible(UUID parcelId, UUID viewerId, boolean admin) {
        Parcel p = parcels.findById(parcelId).orElseThrow(() -> ApiException.notFound("Parcel"));
        if (admin || p.getSenderId().equals(viewerId)) return p;
        if (p.getTripId() != null && p.getStatus() != Status.CREATED
                && trips.findById(p.getTripId()).map(t -> t.getTravellerId().equals(viewerId)).orElse(false)) {
            return p;
        }
        throw ApiException.notFound("Parcel");   // 404, not 403: do not leak existence
    }

    @Transactional(readOnly = true)
    public List<Parcel> sent(UUID senderId) { return parcels.findBySenderIdOrderByCreatedAtDesc(senderId); }

    @Transactional(readOnly = true)
    public List<Parcel> assigned(UUID travellerId) { return parcels.findAssignedTo(travellerId); }

    @Transactional(readOnly = true)
    public List<ParcelEvent> timeline(UUID parcelId, UUID viewerId, boolean admin) {
        visible(parcelId, viewerId, admin);
        return events.findByParcelIdOrderByCreatedAtAsc(parcelId);
    }

    /** Builds the API view, hiding secrets from whoever should not see them. */
    @Transactional(readOnly = true)
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
        record(p, "COMPLETED", actor, note);
        if (p.getTripId() != null) notifyTraveller(p.getTripId(), p, "Payment released for a delivered parcel");
        return p;
    }

    private void notifyTraveller(UUID tripId, Parcel p, String msg) {
        trips.findById(tripId).ifPresent(t -> notifications.notify(t.getTravellerId(), p.getId(), msg));
    }

    private Parcel lock(UUID id) {
        return parcels.findForUpdate(id).orElseThrow(() -> ApiException.notFound("Parcel"));
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
        events.save(e);
    }

    private static String newToken() {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
