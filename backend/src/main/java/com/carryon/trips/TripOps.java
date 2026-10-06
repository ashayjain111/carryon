package com.carryon.trips;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/** Atomic, single-document operations on trips. This is where overbooking is prevented. */
@Component
@RequiredArgsConstructor
public class TripOps {
    private final MongoTemplate mongo;

    /**
     * Reserve capacity. The filter (status OPEN and availableG >= grams) and the $inc are applied atomically to
     * one document, so two concurrent bookings can never both succeed past the limit.
     * Returns true if reserved, false if the trip is full or closed.
     */
    public boolean reserve(UUID tripId, int grams) {
        Query q = new Query(Criteria.where("_id").is(tripId).and("status").is(Trip.Status.OPEN)
                .and("availableG").gte(grams));
        Update u = new Update().inc("availableG", -grams).inc("bookedG", grams);
        return mongo.updateFirst(q, u, Trip.class).getModifiedCount() == 1;
    }

    public void release(UUID tripId, int grams) {
        Query q = new Query(Criteria.where("_id").is(tripId).and("bookedG").gte(grams));
        mongo.updateFirst(q, new Update().inc("availableG", grams).inc("bookedG", -grams), Trip.class);
    }

    public void cancel(UUID tripId) {
        mongo.updateFirst(new Query(Criteria.where("_id").is(tripId)),
                new Update().set("status", Trip.Status.CANCELLED), Trip.class);
    }

    public List<Trip> search(String origin, String destination, LocalDate from, LocalDate to, int minGrams, int limit) {
        Query q = new Query(Criteria.where("status").is(Trip.Status.OPEN)
                .and("originKey").is(origin.trim().toLowerCase())
                .and("destinationKey").is(destination.trim().toLowerCase())
                .and("departureDate").gte(from).lte(to)
                .and("availableG").gte(minGrams))
                .with(Sort.by("departureDate")).limit(limit);
        return mongo.find(q, Trip.class);
    }
}
