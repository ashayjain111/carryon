package com.carryon.disputes;

import java.util.List;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DisputeRepository extends MongoRepository<Dispute, UUID> {
    List<Dispute> findByStatusOrderByCreatedAtAsc(Dispute.Status status);
    List<Dispute> findByOpenedByOrderByCreatedAtDesc(UUID userId);
}
