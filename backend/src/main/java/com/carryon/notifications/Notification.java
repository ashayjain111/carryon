package com.carryon.notifications;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("notifications")
@Getter @Setter @NoArgsConstructor
public class Notification {
    @Id private UUID id = UUID.randomUUID();
    private UUID userId;
    private UUID parcelId;
    private String message;
    private boolean read;
    private Instant createdAt = Instant.now();
}
