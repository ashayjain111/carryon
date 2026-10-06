package com.carryon.parcels;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Append-only audit record: who did what to a parcel, and when. */
@Document("parcel_events")
@Getter @Setter @NoArgsConstructor
public class ParcelEvent {
    @Id private UUID id = UUID.randomUUID();
    private UUID parcelId;
    private String type;
    private UUID actorId;
    private String note;
    private Instant createdAt = Instant.now();
}
