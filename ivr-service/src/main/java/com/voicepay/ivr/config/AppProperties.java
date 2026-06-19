package com.voicepay.ivr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String userServiceUrl;
    private String paymentServiceUrl;

    @NestedConfigurationProperty
    private Scheduler scheduler = new Scheduler();

    private String userServiceBaseUrl;

    @NestedConfigurationProperty
    private NotificationService notificationService = new NotificationService();

    @NestedConfigurationProperty
    private Otp otp = new Otp();

    @Data
    public static class Scheduler {
        private String cron = "0/20 * * * * ?";
        private int chunkSize = 5;
        private int maxConcurrentCalls = 5;
        private boolean forceMock = false;

        @NestedConfigurationProperty
        private CommercialHours commercialHours = new CommercialHours();
        private String allowedDaysOfWeek = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY";
        private String timezoneFallback = "Europe/Madrid";

        @Data
        public static class CommercialHours {
            private String start = "09:00";
            private String end = "21:00";
        }
    }

    @Data
    public static class NotificationService {
        private String url;
    }

    @Data
    public static class Otp {
        private Double threshold;
    }
}
