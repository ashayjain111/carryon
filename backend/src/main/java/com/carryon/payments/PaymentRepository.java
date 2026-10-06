package com.carryon.payments;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PaymentRepository extends MongoRepository<Payment, UUID> {
    Optional<Payment> findByParcelId(UUID parcelId);
}
