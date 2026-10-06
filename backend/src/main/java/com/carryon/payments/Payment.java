package com.carryon.payments;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("payments")
@Getter @Setter @NoArgsConstructor
public class Payment {
    public enum Status { HELD, RELEASED, REFUNDED }

    @Id private UUID id = UUID.randomUUID();
    @Version private Long version;
    private UUID parcelId;          // unique index: one payment per parcel
    private UUID payerId;
    private long amountPaise;
    private long platformFeePaise;
    private Status status = Status.HELD;
    private Instant createdAt = Instant.now();
    private Instant settledAt;
}
