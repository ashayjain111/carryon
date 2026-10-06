package com.carryon.disputes;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("disputes")
@Getter @Setter @NoArgsConstructor
public class Dispute {
    public enum Kind { LOST, DAMAGED, NOT_DELIVERED, OTHER }
    public enum Status { OPEN, REFUNDED, RELEASED }

    @Id private UUID id = UUID.randomUUID();
    @Version private Long version;
    private UUID parcelId;
    private UUID openedBy;
    private Kind kind;
    private String description;
    private Status status = Status.OPEN;
    private String resolutionNote;
    private UUID resolvedBy;
    private Instant createdAt = Instant.now();
    private Instant resolvedAt;
}
