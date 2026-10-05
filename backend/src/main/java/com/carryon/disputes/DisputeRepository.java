package com.carryon.disputes;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {
    List<Dispute> findByStatusOrderByCreatedAtAsc(Dispute.Status status);
    List<Dispute> findByOpenedByOrderByCreatedAtDesc(UUID userId);
}
