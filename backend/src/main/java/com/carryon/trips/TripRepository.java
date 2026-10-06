package com.carryon.trips;

import java.util.List;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TripRepository extends MongoRepository<Trip, UUID> {
    List<Trip> findByTravellerIdOrderByDepartureDateDesc(UUID travellerId);
}
