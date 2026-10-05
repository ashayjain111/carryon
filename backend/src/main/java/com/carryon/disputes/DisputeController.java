package com.carryon.disputes;

import com.carryon.common.ApiException;
import com.carryon.common.CurrentUser;
import com.carryon.parcels.Parcel;
import com.carryon.parcels.ParcelService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DisputeController {
    private final DisputeRepository disputes;
    private final ParcelService parcels;

    public record OpenRequest(@NotNull Dispute.Kind kind, @NotBlank @Size(max = 1000) String description) {}
    public record ResolveRequest(@NotNull Decision decision, @Size(max = 500) String note) {}
    public enum Decision { REFUND_SENDER, RELEASE_TO_TRAVELLER }
    public record DisputeView(UUID id, UUID parcelId, UUID openedBy, String kind, String description,
                              String status, String resolutionNote, Instant createdAt, Instant resolvedAt) {
        static DisputeView of(Dispute d) {
            return new DisputeView(d.getId(), d.getParcelId(), d.getOpenedBy(), d.getKind().name(), d.getDescription(),
                    d.getStatus().name(), d.getResolutionNote(), d.getCreatedAt(), d.getResolvedAt());
        }
    }

    /** Sender or traveller reports loss/damage. Freezes the parcel (and its escrow) until an admin decides. */
    @PostMapping("/parcels/{parcelId}/disputes")
    @Transactional
    public DisputeView open(Authentication a, @PathVariable UUID parcelId, @Valid @RequestBody OpenRequest r) {
        UUID me = CurrentUser.id(a);
        parcels.visible(parcelId, me, false);          // 404 unless a participant
        parcels.markDisputed(parcelId, me);            // locks the parcel, validates state
        Dispute d = new Dispute();
        d.setParcelId(parcelId);
        d.setOpenedBy(me);
        d.setKind(r.kind());
        d.setDescription(r.description());
        return DisputeView.of(disputes.saveAndFlush(d));
    }

    @GetMapping("/disputes/mine")
    public List<DisputeView> mine(Authentication a) {
        return disputes.findByOpenedByOrderByCreatedAtDesc(CurrentUser.id(a)).stream().map(DisputeView::of).toList();
    }

    @GetMapping("/admin/disputes")
    public List<DisputeView> openDisputes() {
        return disputes.findByStatusOrderByCreatedAtAsc(Dispute.Status.OPEN).stream().map(DisputeView::of).toList();
    }

    @PostMapping("/admin/disputes/{id}/resolve")
    @Transactional
    public DisputeView resolve(Authentication a, @PathVariable UUID id, @Valid @RequestBody ResolveRequest r) {
        UUID admin = CurrentUser.id(a);
        Dispute d = disputes.findById(id).orElseThrow(() -> ApiException.notFound("Dispute"));
        if (d.getStatus() != Dispute.Status.OPEN) throw ApiException.conflict("Dispute already resolved");
        boolean refund = r.decision() == Decision.REFUND_SENDER;
        parcels.settleDispute(d.getParcelId(), admin, refund);
        d.setStatus(refund ? Dispute.Status.REFUNDED : Dispute.Status.RELEASED);
        d.setResolutionNote(r.note());
        d.setResolvedBy(admin);
        d.setResolvedAt(Instant.now());
        return DisputeView.of(d);
    }
}
