package com.carryon.parcels;

import java.util.List;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ParcelEventRepository extends MongoRepository<ParcelEvent, UUID> {
    List<ParcelEvent> findByParcelIdOrderByCreatedAtAsc(UUID parcelId);
}
