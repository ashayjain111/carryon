package com.carryon.parcels;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Append-only audit record: who did what to a parcel, and when. */
@Entity
@Table(name = "parcel_events")
@Getter @Setter @NoArgsConstructor
public class ParcelEvent {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "parcel_id", nullable = false)
    private UUID parcelId;

    @Column(nullable = false)
    private String type;

    @Column(name = "actor_id")
    private UUID actorId;

    private String note;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
