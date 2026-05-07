package com.voicepay.notification.service;

import com.voicepay.notification.model.Notification;
import com.voicepay.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public List<Notification> getAllNotifications() {
        return notificationRepository.findAll();
    }

    public List<Notification> getRecentNotifications() {
        return notificationRepository.findTop10ByOrderByCreatedAtDesc();
    }

    public Notification sendNotification(Notification notification) {
        log.info("Simulating sending {} notification to {}: {}", 
                 notification.getType(), notification.getRecipient(), notification.getMessage());
        
        // Simulación: Siempre se envía correctamente por ahora
        notification.setStatus(Notification.NotificationStatus.SENT);
        
        return notificationRepository.save(notification);
    }
}
