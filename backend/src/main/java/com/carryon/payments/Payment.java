package com.carryon.payments;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payments")
@Getter @Setter @NoArgsConstructor
public class Payment {
    public enum Status { HELD, RELEASED, REFUNDED }

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "parcel_id", nullable = false, unique = true)
    private UUID parcelId;

    @Column(name = "payer_id", nullable = false)
    private UUID payerId;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(name = "platform_fee_paise", nullable = false)
    private long platformFeePaise;

    @Enumerated(EnumType.STRING)
    private Status status = Status.HELD;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "settled_at")
    private Instant settledAt;
}
