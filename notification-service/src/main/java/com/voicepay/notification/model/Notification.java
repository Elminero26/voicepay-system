package com.voicepay.notification.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String recipient; // Phone number or email
    private String message;
    
    @Enumerated(EnumType.STRING)
    private NotificationType type; // SMS, EMAIL, PUSH

    @Enumerated(EnumType.STRING)
    private NotificationStatus status; // PENDING, SENT, FAILED

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = NotificationStatus.PENDING;
    }

    public enum NotificationType {
        SMS, EMAIL, PUSH
    }

    public enum NotificationStatus {
        PENDING, SENT, FAILED
    }
}
