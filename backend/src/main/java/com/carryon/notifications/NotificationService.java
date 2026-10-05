package com.carryon.notifications;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * In-app notifications persisted in PostgreSQL; the client polls. Email/SMS (SES/SNS) would be added
 * behind this same method, called synchronously or via an outbox if delivery latency ever matters.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository repo;

    public void notify(UUID userId, UUID parcelId, String message) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setParcelId(parcelId);
        n.setMessage(message);
        repo.save(n);
    }
}
