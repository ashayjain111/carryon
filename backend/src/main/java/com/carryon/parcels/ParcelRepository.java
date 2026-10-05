package com.carryon.parcels;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParcelRepository extends JpaRepository<Parcel, UUID> {

    /** SELECT ... FOR UPDATE: serialises state transitions on one parcel (double-accept, double-deliver). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Parcel p where p.id = :id")
    Optional<Parcel> findForUpdate(@Param("id") UUID id);

    List<Parcel> findBySenderIdOrderByCreatedAtDesc(UUID senderId);

    @Query("""
            select p from Parcel p
             where p.tripId in (select t.id from com.carryon.trips.Trip t where t.travellerId = :travellerId)
               and p.status <> com.carryon.parcels.Parcel.Status.CREATED
             order by p.createdAt desc
            """)
    List<Parcel> findAssignedTo(@Param("travellerId") UUID travellerId);

    boolean existsByTripIdAndStatusIn(UUID tripId, Collection<Parcel.Status> statuses);

    List<Parcel> findByStatusAndDeliveredAtBefore(Parcel.Status status, Instant before);

    List<Parcel> findByStatusOrderByCreatedAtDesc(Parcel.Status status);
}
