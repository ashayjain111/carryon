package com.carryon.ratings;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ratings")
@Getter @Setter @NoArgsConstructor
public class Rating {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "parcel_id", nullable = false)
    private UUID parcelId;

    @Column(name = "rater_id", nullable = false)
    private UUID raterId;

    @Column(name = "ratee_id", nullable = false)
    private UUID rateeId;

    private int score;

    private String comment;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
