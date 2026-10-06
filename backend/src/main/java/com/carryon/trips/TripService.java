package com.carryon.trips;

import com.carryon.common.ApiException;
import com.carryon.parcels.Parcel;
import com.carryon.parcels.ParcelRepository;
import com.carryon.users.User;
import com.carryon.users.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TripService {
    private final TripRepository trips;
    private final TripOps tripOps;
    private final UserRepository users;
    private final ParcelRepository parcels;

    public Trip create(UUID travellerId, TripController.CreateTripRequest r) {
        User u = users.findById(travellerId).orElseThrow(() -> ApiException.notFound("User"));
        if (u.getKycStatus() != User.KycStatus.VERIFIED) {
            throw ApiException.forbidden("Complete identity verification before posting a trip");
        }
        if (r.originCity().trim().equalsIgnoreCase(r.destinationCity().trim())) {
            throw ApiException.badRequest("Origin and destination must differ");
        }
        if (r.departureDate().isBefore(LocalDate.now())) {
            throw ApiException.badRequest("Departure date is in the past");
        }
        if (r.personalLuggageG() >= r.totalCapacityG()) {
            throw ApiException.badRequest("Personal luggage leaves no spare capacity");
        }
        Trip t = new Trip();
        t.setTravellerId(travellerId);
        t.setOriginCity(r.originCity().trim());
        t.setDestinationCity(r.destinationCity().trim());
        t.setOriginKey(t.getOriginCity().toLowerCase());
        t.setDestinationKey(t.getDestinationCity().toLowerCase());
        t.setDepartureDate(r.departureDate());
        t.setTotalCapacityG(r.totalCapacityG());
        t.setPersonalLuggageG(r.personalLuggageG());
        t.setBookedG(0);
        t.setAvailableG(r.totalCapacityG() - r.personalLuggageG());
        return trips.insert(t);
    }

    public List<Trip> mine(UUID travellerId) {
        return trips.findByTravellerIdOrderByDepartureDateDesc(travellerId);
    }

    public void cancel(UUID travellerId, UUID tripId) {
        Trip t = trips.findById(tripId).orElseThrow(() -> ApiException.notFound("Trip"));
        if (!t.getTravellerId().equals(travellerId)) throw ApiException.forbidden("Not your trip");
        Set<Parcel.Status> active = Set.of(Parcel.Status.ACCEPTED, Parcel.Status.PICKED_UP,
                Parcel.Status.DELIVERED, Parcel.Status.DISPUTED);
        if (parcels.existsByTripIdAndStatusIn(tripId, active)) {
            throw ApiException.conflict("Trip has active parcels and cannot be cancelled");
        }
        tripOps.cancel(tripId);
    }
}
