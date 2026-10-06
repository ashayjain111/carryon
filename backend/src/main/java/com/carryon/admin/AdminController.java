package com.carryon.admin;

import com.carryon.common.ApiException;
import com.carryon.parcels.Parcel;
import com.carryon.parcels.ParcelRepository;
import com.carryon.users.User;
import com.carryon.users.UserController.UserView;
import com.carryon.users.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

/** All /api/admin/** routes require ROLE_ADMIN (see SecurityConfig). Dispute routes live in the disputes module. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final UserRepository users;
    private final ParcelRepository parcels;

    public record EnabledRequest(boolean enabled) {}
    public record AdminParcel(UUID id, String status, String originCity, String destinationCity, long rewardPaise, UUID senderId, UUID tripId) {}

    @GetMapping("/users")
    public List<UserView> users() {
        return users.findAll(PageRequest.of(0, 200)).stream().map(UserView::of).toList();
    }

    @PostMapping("/users/{id}/enabled")
    public UserView setEnabled(@PathVariable UUID id, @RequestBody EnabledRequest r) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (u.getRole() == User.Role.ADMIN) throw ApiException.badRequest("Cannot disable an admin");
        u.setEnabled(r.enabled());
        users.save(u);
        return UserView.of(u);
    }

    @GetMapping("/parcels")
    public List<AdminParcel> parcels(@RequestParam(required = false) Parcel.Status status) {
        List<Parcel> list = status == null
                ? parcels.findAll(PageRequest.of(0, 200)).getContent()
                : parcels.findByStatusOrderByCreatedAtDesc(status);
        return list.stream().map(p -> new AdminParcel(p.getId(), p.getStatus().name(), p.getOriginCity(),
                p.getDestinationCity(), p.getRewardPaise(), p.getSenderId(), p.getTripId())).toList();
    }
}
