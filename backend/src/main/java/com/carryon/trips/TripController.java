package com.carryon.trips;

import com.carryon.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {
    private final TripService service;

    public record CreateTripRequest(
            @NotBlank @Size(max = 80) String originCity,
            @NotBlank @Size(max = 80) String destinationCity,
            @NotNull LocalDate departureDate,
            @Min(1000) @Max(40000) int totalCapacityG,
            @Min(0) @Max(40000) int personalLuggageG) {}

    public record TripView(UUID id, UUID travellerId, String originCity, String destinationCity,
                           LocalDate departureDate, int totalCapacityG, int personalLuggageG,
                           int bookedG, int availableG, String status, Double travellerRating) {
        public static TripView of(Trip t, Double rating) {
            return new TripView(t.getId(), t.getTravellerId(), t.getOriginCity(), t.getDestinationCity(),
                    t.getDepartureDate(), t.getTotalCapacityG(), t.getPersonalLuggageG(),
                    t.getBookedG(), t.getAvailableG(), t.getStatus().name(), rating);
        }
    }

    @PostMapping
    public TripView create(Authentication auth, @Valid @RequestBody CreateTripRequest r) {
        return TripView.of(service.create(CurrentUser.id(auth), r), null);
    }

    @GetMapping("/mine")
    public List<TripView> mine(Authentication auth) {
        return service.mine(CurrentUser.id(auth)).stream().map(t -> TripView.of(t, null)).toList();
    }

    @DeleteMapping("/{id}")
    public void cancel(Authentication auth, @PathVariable UUID id) {
        service.cancel(CurrentUser.id(auth), id);
    }
}
