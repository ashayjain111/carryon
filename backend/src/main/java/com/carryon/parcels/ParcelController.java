package com.carryon.parcels;

import com.carryon.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parcels")
@RequiredArgsConstructor
public class ParcelController {
    private final ParcelService service;

    public record CreateParcelRequest(
            @NotBlank @Size(max = 300) String description,
            @Min(1) @Max(20000) int weightG,
            @Min(0) long declaredValuePaise,
            @Min(100) long rewardPaise,
            @NotBlank @Size(max = 80) String originCity,
            @NotBlank @Size(max = 80) String destinationCity,
            @NotBlank @Size(max = 120) String recipientName,
            @NotBlank @Pattern(regexp = "^[+0-9 ]{7,20}$", message = "invalid phone") String recipientPhone,
            boolean noProhibitedItems) {}

    public record TripRef(@NotNull UUID tripId) {}
    public record TokenRequest(@NotBlank String token) {}

    public record ParcelView(UUID id, String status, String description, int weightG, long declaredValuePaise,
                             long rewardPaise, String originCity, String destinationCity, String recipientName,
                             String recipientPhone, UUID tripId, String senderName, String travellerName,
                             String pickupToken, String deliveryToken, Instant createdAt, Instant deliveredAt) {}

    public record EventView(String type, UUID actorId, String note, Instant createdAt) {}

    private ParcelView v(Authentication a, Parcel p) {
        return service.view(p, CurrentUser.id(a), CurrentUser.isAdmin(a));
    }

    @PostMapping
    public ParcelView create(Authentication a, @Valid @RequestBody CreateParcelRequest r) {
        return v(a, service.create(CurrentUser.id(a), r));
    }

    @GetMapping("/sent")
    public List<ParcelView> sent(Authentication a) {
        return service.sent(CurrentUser.id(a)).stream().map(p -> v(a, p)).toList();
    }

    @GetMapping("/assigned")
    public List<ParcelView> assigned(Authentication a) {
        return service.assigned(CurrentUser.id(a)).stream().map(p -> v(a, p)).toList();
    }

    @GetMapping("/{id}")
    public ParcelView get(Authentication a, @PathVariable UUID id) {
        return v(a, service.visible(id, CurrentUser.id(a), CurrentUser.isAdmin(a)));
    }

    @GetMapping("/{id}/events")
    public List<EventView> events(Authentication a, @PathVariable UUID id) {
        return service.timeline(id, CurrentUser.id(a), CurrentUser.isAdmin(a)).stream()
                .map(e -> new EventView(e.getType(), e.getActorId(), e.getNote(), e.getCreatedAt())).toList();
    }

    @PostMapping("/{id}/request")
    public ParcelView request(Authentication a, @PathVariable UUID id, @Valid @RequestBody TripRef r) {
        return v(a, service.requestTrip(CurrentUser.id(a), id, r.tripId()));
    }

    @PostMapping("/{id}/cancel")
    public ParcelView cancel(Authentication a, @PathVariable UUID id) {
        return v(a, service.cancel(CurrentUser.id(a), id));
    }

    @PostMapping("/{id}/confirm")
    public ParcelView confirm(Authentication a, @PathVariable UUID id) {
        return v(a, service.confirmDelivery(CurrentUser.id(a), id));
    }

    @PostMapping("/{id}/accept")
    public ParcelView accept(Authentication a, @PathVariable UUID id) {
        return v(a, service.accept(CurrentUser.id(a), id));
    }

    @PostMapping("/{id}/reject")
    public ParcelView reject(Authentication a, @PathVariable UUID id) {
        return v(a, service.reject(CurrentUser.id(a), id));
    }

    @PostMapping("/{id}/pickup")
    public ParcelView pickup(Authentication a, @PathVariable UUID id, @Valid @RequestBody TokenRequest r) {
        return v(a, service.pickup(CurrentUser.id(a), id, r.token()));
    }

    @PostMapping("/{id}/deliver")
    public ParcelView deliver(Authentication a, @PathVariable UUID id, @Valid @RequestBody TokenRequest r) {
        return v(a, service.deliver(CurrentUser.id(a), id, r.token()));
    }
}
