package com.carryon.parcels;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParcelEventRepository extends JpaRepository<ParcelEvent, UUID> {
    List<ParcelEvent> findByParcelIdOrderByCreatedAtAsc(UUID parcelId);
}
