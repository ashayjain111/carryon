package com.carryon.parcels;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "parcels")
@Getter @Setter @NoArgsConstructor
public class Parcel {
    /**
     * CREATED -> REQUESTED -> ACCEPTED -> PICKED_UP -> DELIVERED -> COMPLETED
     * CANCELLED from CREATED/REQUESTED/ACCEPTED; DISPUTED from ACCEPTED/PICKED_UP/DELIVERED (then COMPLETED or REFUNDED by admin).
     */
    public enum Status { CREATED, REQUESTED, ACCEPTED, PICKED_UP, DELIVERED, COMPLETED, CANCELLED, DISPUTED, REFUNDED }

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "trip_id")
    private UUID tripId;

    @Column(nullable = false)
    private String description;

    @Column(name = "weight_g", nullable = false)
    private int weightG;

    @Column(name = "declared_value_paise", nullable = false)
    private long declaredValuePaise;

    @Column(name = "reward_paise", nullable = false)
    private long rewardPaise;

    @Column(name = "origin_city", nullable = false)
    private String originCity;

    @Column(name = "destination_city", nullable = false)
    private String destinationCity;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false)
    private String recipientPhone;

    @Enumerated(EnumType.STRING)
    private Status status = Status.CREATED;

    @Column(name = "pickup_token", nullable = false)
    private String pickupToken;

    @Column(name = "delivery_token", nullable = false)
    private String deliveryToken;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;
}
