package com.voicepay.notification.repository;

import com.voicepay.notification.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipient(String recipient);
    List<Notification> findTop10ByOrderByCreatedAtDesc();
}
