package com.carryon.notifications;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notifications")
@Getter @Setter @NoArgsConstructor
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "parcel_id")
    private UUID parcelId;

    @Column(nullable = false)
    private String message;

    @Column(name = "is_read")
    private boolean read;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
