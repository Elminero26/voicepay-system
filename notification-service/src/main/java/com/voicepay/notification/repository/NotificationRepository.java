package com.voicepay.notification.repository;

import com.voicepay.notification.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipient(String recipient);
    List<Notification> findTop10ByOrderByCreatedAtDesc();
}
