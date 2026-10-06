package com.carryon.parcels;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("parcels")
@Getter @Setter @NoArgsConstructor
public class Parcel {
    /**
     * CREATED -> REQUESTED -> ACCEPTED -> PICKED_UP -> DELIVERED -> COMPLETED
     * CANCELLED from CREATED/REQUESTED/ACCEPTED; DISPUTED from ACCEPTED/PICKED_UP/DELIVERED (then COMPLETED or REFUNDED by admin).
     */
    public enum Status { CREATED, REQUESTED, ACCEPTED, PICKED_UP, DELIVERED, COMPLETED, CANCELLED, DISPUTED, REFUNDED }

    @Id private UUID id = UUID.randomUUID();

    /** Optimistic lock: two transactions changing the same parcel cannot both commit. */
    @Version private Long version;

    private UUID senderId;
    private UUID tripId;
    private String description;
    private int weightG;
    private long declaredValuePaise;
    private long rewardPaise;
    private String originCity;
    private String destinationCity;
    private String recipientName;
    private String recipientPhone;
    private Status status = Status.CREATED;
    private String pickupToken;     // QR shown by sender to traveller
    private String deliveryToken;   // QR shown by recipient to traveller
    private Instant createdAt = Instant.now();
    private Instant deliveredAt;
}
