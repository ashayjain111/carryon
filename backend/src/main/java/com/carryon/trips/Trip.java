package com.carryon.trips;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("trips")
@Getter @Setter @NoArgsConstructor
public class Trip {
    public enum Status { OPEN, CANCELLED }

    @Id private UUID id = UUID.randomUUID();
    private UUID travellerId;
    private String originCity;
    private String destinationCity;
    private String originKey;          // lower-cased, for indexed case-insensitive matching
    private String destinationKey;
    private LocalDate departureDate;
    private int totalCapacityG;
    private int personalLuggageG;
    /**
     * bookedG and availableG are written ONLY by TripOps.reserve/release (atomic conditional updates).
     * availableG is stored, not derived, so the "is there room?" check and the decrement happen in one
     * atomic single-document operation. Never call save() on an existing trip: it would overwrite
     * these counters with stale values.
     */
    private int bookedG;
    private int availableG;
    private Status status = Status.OPEN;
    private Instant createdAt = Instant.now();

    public int availableG() { return availableG; }
}
