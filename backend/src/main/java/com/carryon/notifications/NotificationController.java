package com.carryon.notifications;

import com.carryon.common.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationRepository repo;
    private final MongoTemplate mongo;

    public record NotificationView(UUID id, UUID parcelId, String message, boolean read, Instant createdAt) {}

    @GetMapping
    public List<NotificationView> list(Authentication auth) {
        return repo.findByUserIdOrderByCreatedAtDesc(CurrentUser.id(auth), PageRequest.of(0, 50)).stream()
                .map(n -> new NotificationView(n.getId(), n.getParcelId(), n.getMessage(), n.isRead(), n.getCreatedAt()))
                .toList();
    }

    @PostMapping("/read-all")
    public void readAll(Authentication auth) {
        mongo.updateMulti(new Query(Criteria.where("userId").is(CurrentUser.id(auth)).and("read").is(false)),
                new Update().set("read", true), Notification.class);
    }
}
