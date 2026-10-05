package com.carryon.disputes;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "disputes")
@Getter @Setter @NoArgsConstructor
public class Dispute {
    public enum Kind { LOST, DAMAGED, NOT_DELIVERED, OTHER }
    public enum Status { OPEN, REFUNDED, RELEASED }

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "parcel_id", nullable = false)
    private UUID parcelId;

    @Column(name = "opened_by", nullable = false)
    private UUID openedBy;

    @Enumerated(EnumType.STRING)
    private Kind kind;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    private Status status = Status.OPEN;

    @Column(name = "resolution_note")
    private String resolutionNote;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
