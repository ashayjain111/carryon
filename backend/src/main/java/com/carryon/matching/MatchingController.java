package com.carryon.matching;

import com.carryon.common.ApiException;
import com.carryon.common.CurrentUser;
import com.carryon.parcels.Parcel;
import com.carryon.parcels.ParcelService;
import com.carryon.ratings.RatingRepository;
import com.carryon.trips.Trip;
import com.carryon.trips.TripController.TripView;
import com.carryon.trips.TripRepository;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Matching is a plain indexed SQL query (route + date window + spare capacity) followed by an in-memory sort by
 * traveller rating. At MVP scale this is a few rows; no search engine required.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MatchingController {
    private final TripRepository trips;
    private final RatingRepository ratings;
    private final ParcelService parcelService;

    @GetMapping("/trips/search")
    @Transactional(readOnly = true)
    public List<TripView> search(@RequestParam String origin, @RequestParam String destination,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                 @RequestParam(defaultValue = "0") int minGrams) {
        LocalDate start = from == null || from.isBefore(LocalDate.now()) ? LocalDate.now() : from;
        LocalDate end = to == null ? start.plusDays(60) : to;
        return rank(trips.search(origin.trim(), destination.trim(), start, end, Math.max(minGrams, 0), PageRequest.of(0, 50)));
    }

    /** Suggested trips for one of my parcels: same route, departing within 30 days, enough spare capacity. */
    @GetMapping("/parcels/{id}/matches")
    @Transactional(readOnly = true)
    public List<TripView> matches(Authentication a, @PathVariable UUID id) {
        Parcel p = parcelService.visible(id, CurrentUser.id(a), false);
        if (!p.getSenderId().equals(CurrentUser.id(a))) throw ApiException.notFound("Parcel");
        LocalDate today = LocalDate.now();
        List<Trip> found = trips.search(p.getOriginCity(), p.getDestinationCity(), today, today.plusDays(30),
                p.getWeightG(), PageRequest.of(0, 50));
        return rank(found.stream().filter(t -> !t.getTravellerId().equals(p.getSenderId())).toList());
    }

    private List<TripView> rank(List<Trip> found) {
        Map<UUID, Double> avg = new HashMap<>();
        if (!found.isEmpty()) {
            var ids = found.stream().map(Trip::getTravellerId).collect(Collectors.toSet());
            for (Object[] row : ratings.averages(ids)) avg.put((UUID) row[0], ((Number) row[1]).doubleValue());
        }
        return found.stream()
                .map(t -> TripView.of(t, avg.get(t.getTravellerId())))
                .sorted(Comparator.comparing((TripView v) -> v.travellerRating() == null ? -1.0 : v.travellerRating()).reversed()
                        .thenComparing(TripView::departureDate))
                .toList();
    }
}
