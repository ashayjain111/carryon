package com.carryon.ratings;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("ratings")
@Getter @Setter @NoArgsConstructor
public class Rating {
    @Id private UUID id = UUID.randomUUID();
    private UUID parcelId;
    private UUID raterId;
    private UUID rateeId;
    private int score;
    private String comment;
    private Instant createdAt = Instant.now();
}
