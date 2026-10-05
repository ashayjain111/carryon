package com.carryon.trips;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "trips")
@Getter @Setter @NoArgsConstructor
public class Trip {
    public enum Status { OPEN, CANCELLED }

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "traveller_id", nullable = false)
    private UUID travellerId;

    @Column(name = "origin_city", nullable = false)
    private String originCity;

    @Column(name = "destination_city", nullable = false)
    private String destinationCity;

    @Column(name = "departure_date", nullable = false)
    private LocalDate departureDate;

    @Column(name = "total_capacity_g", nullable = false)
    private int totalCapacityG;

    @Column(name = "personal_luggage_g", nullable = false)
    private int personalLuggageG;

    /** Never written through the entity: only via TripRepository.reserve/release (atomic SQL). */
    @Column(name = "booked_g", insertable = false, updatable = false)
    private int bookedG;

    @Enumerated(EnumType.STRING)
    private Status status = Status.OPEN;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public int availableG() { return totalCapacityG - personalLuggageG - bookedG; }
}
