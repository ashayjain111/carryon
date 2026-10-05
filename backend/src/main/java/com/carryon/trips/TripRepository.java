package com.carryon.trips;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    List<Trip> findByTravellerIdOrderByDepartureDateDesc(UUID travellerId);

    /**
     * Atomically reserves capacity. The WHERE clause is the whole concurrency story: the row lock taken by
     * UPDATE serialises competing bookings, and the capacity check is re-evaluated against the latest
     * committed value. Returns 1 if reserved, 0 if the trip is full/closed. No application-level lock needed.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE trips SET booked_g = booked_g + :grams
             WHERE id = :id AND status = 'OPEN'
               AND personal_luggage_g + booked_g + :grams <= total_capacity_g
            """, nativeQuery = true)
    int reserve(@Param("id") UUID id, @Param("grams") int grams);

    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE trips SET booked_g = booked_g - :grams WHERE id = :id AND booked_g >= :grams",
            nativeQuery = true)
    int release(@Param("id") UUID id, @Param("grams") int grams);

    @Query("""
            select t from Trip t
             where t.status = com.carryon.trips.Trip.Status.OPEN
               and lower(t.originCity) = lower(:origin)
               and lower(t.destinationCity) = lower(:destination)
               and t.departureDate between :from and :to
               and (t.totalCapacityG - t.personalLuggageG - t.bookedG) >= :minGrams
             order by t.departureDate
            """)
    List<Trip> search(@Param("origin") String origin, @Param("destination") String destination,
                      @Param("from") LocalDate from, @Param("to") LocalDate to,
                      @Param("minGrams") int minGrams, Pageable page);
}
