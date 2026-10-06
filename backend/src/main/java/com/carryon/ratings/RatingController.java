package com.carryon.ratings;

import com.carryon.common.ApiException;
import com.carryon.common.CurrentUser;
import com.carryon.parcels.Parcel;
import com.carryon.parcels.ParcelRepository;
import com.carryon.trips.TripRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RatingController {
    private final RatingRepository ratings;
    private final ParcelRepository parcels;
    private final TripRepository trips;

    public record RateRequest(@Min(1) @Max(5) int score, @Size(max = 500) String comment) {}
    public record RatingView(int score, String comment, Instant createdAt) {}
    public record Summary(double average, int count, List<RatingView> recent) {}

    /** Sender and traveller each rate the other once, after the parcel is COMPLETED. */
    @PostMapping("/parcels/{parcelId}/ratings")
    public RatingView rate(Authentication a, @PathVariable UUID parcelId, @Valid @RequestBody RateRequest r) {
        UUID me = CurrentUser.id(a);
        Parcel p = parcels.findById(parcelId).orElseThrow(() -> ApiException.notFound("Parcel"));
        if (p.getStatus() != Parcel.Status.COMPLETED) throw ApiException.conflict("Parcel is not completed yet");
        UUID traveller = trips.findById(p.getTripId()).orElseThrow().getTravellerId();
        UUID ratee;
        if (me.equals(p.getSenderId())) ratee = traveller;
        else if (me.equals(traveller)) ratee = p.getSenderId();
        else throw ApiException.notFound("Parcel");
        Rating rt = new Rating();
        rt.setParcelId(parcelId);
        rt.setRaterId(me);
        rt.setRateeId(ratee);
        rt.setScore(r.score());
        rt.setComment(r.comment());
        ratings.insert(rt);     // unique index (parcelId, raterId) -> 409 on duplicates
        return new RatingView(rt.getScore(), rt.getComment(), rt.getCreatedAt());
    }

    @GetMapping("/users/{userId}/ratings")
    public Summary summary(@PathVariable UUID userId) {
        List<Rating> all = ratings.findByRateeIdOrderByCreatedAtDesc(userId);
        double avg = all.stream().mapToInt(Rating::getScore).average().orElse(0);
        return new Summary(avg, all.size(), all.stream().limit(10)
                .map(x -> new RatingView(x.getScore(), x.getComment(), x.getCreatedAt())).toList());
    }
}
