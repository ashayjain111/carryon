package com.carryon.parcels;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ParcelRepository extends MongoRepository<Parcel, UUID> {
    List<Parcel> findBySenderIdOrderByCreatedAtDesc(UUID senderId);

    List<Parcel> findByTripIdInAndStatusNotOrderByCreatedAtDesc(Collection<UUID> tripIds, Parcel.Status status);

    boolean existsByTripIdAndStatusIn(UUID tripId, Collection<Parcel.Status> statuses);

    List<Parcel> findByStatusAndDeliveredAtBefore(Parcel.Status status, Instant before);

    List<Parcel> findByStatusOrderByCreatedAtDesc(Parcel.Status status);
}
