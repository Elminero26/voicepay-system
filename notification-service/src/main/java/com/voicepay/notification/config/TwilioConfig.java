package com.voicepay.notification.config;

import com.twilio.Twilio;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class TwilioConfig {

    private final TwilioProperties twilioProperties;

    @PostConstruct
    public void init() {
        if (twilioProperties.getAccountSid() != null 
                && !twilioProperties.getAccountSid().contains("PLACEHOLDER")
                && twilioProperties.getAuthToken() != null 
                && !twilioProperties.getAuthToken().contains("PLACEHOLDER")) {
            Twilio.init(twilioProperties.getAccountSid(), twilioProperties.getAuthToken());
            log.info("Twilio SDK initialized with Account SID: {}", twilioProperties.getAccountSid());
        } else {
            log.warn("Twilio SDK not initialized. Using fallback simulation. Account SID: {}", twilioProperties.getAccountSid());
        }
    }
}
