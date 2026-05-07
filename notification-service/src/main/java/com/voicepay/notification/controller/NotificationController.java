package com.voicepay.notification.controller;

import com.voicepay.notification.model.Notification;
import com.voicepay.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<Notification> getAllNotifications() {
        return notificationService.getAllNotifications();
    }

    @GetMapping("/recent")
    public List<Notification> getRecentNotifications() {
        return notificationService.getRecentNotifications();
    }

    @PostMapping
    public Notification sendNotification(@RequestBody Notification notification) {
        return notificationService.sendNotification(notification);
    }
}
