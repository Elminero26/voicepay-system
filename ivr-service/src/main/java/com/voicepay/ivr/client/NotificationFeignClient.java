package com.voicepay.ivr.client;

import com.voicepay.ivr.dto.NotificationDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "notification-service-otp", url = "${app.notification-service.url:http://localhost:8083}")
public interface NotificationFeignClient {

    @PostMapping("/notifications")
    NotificationDto sendNotification(
            @RequestBody NotificationDto notification,
            @RequestHeader("Authorization") String token
    );
}
