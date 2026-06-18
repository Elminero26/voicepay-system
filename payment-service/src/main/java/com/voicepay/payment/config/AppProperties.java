package com.voicepay.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String userServiceUrl;
    private String encryptionKey;
    private NotificationService notificationService = new NotificationService();
    private Dunning dunning = new Dunning();

    @Data
    public static class NotificationService {
        private String url;
    }

    @Data
    public static class Dunning {
        private java.util.List<Integer> retryDelaysDays = java.util.List.of(1, 3, 7);
    }
}
