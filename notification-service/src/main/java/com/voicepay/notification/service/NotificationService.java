package com.voicepay.notification.service;

import com.twilio.exception.TwilioException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import com.voicepay.notification.config.TwilioProperties;
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
    private final TwilioProperties twilioProperties;

    public List<Notification> getAllNotifications() {
        return notificationRepository.findAll();
    }

    public List<Notification> getRecentNotifications() {
        return notificationRepository.findTop10ByOrderByCreatedAtDesc();
    }

    public Notification sendNotification(Notification notification) {
        String recipient = notification.getRecipient();
        String messageBody = notification.getMessage();
        Notification.NotificationType type = notification.getType();

        // Logs adecuados para auditar el envío de mensajes (ocultando el contenido del código por seguridad)
        String maskedMessage = maskOtp(messageBody);
        log.info("Request received to send notification of type {} to {}: {}", type, recipient, maskedMessage);

        boolean isTwilioConfigured = twilioProperties.getAccountSid() != null 
                && !twilioProperties.getAccountSid().contains("PLACEHOLDER")
                && twilioProperties.getAuthToken() != null 
                && !twilioProperties.getAuthToken().contains("PLACEHOLDER");

        if (type == Notification.NotificationType.SMS && isTwilioConfigured) {
            try {
                boolean isWhatsApp = recipient.startsWith("whatsapp:") || twilioProperties.isWhatsappEnabled();
                
                String cleanRecipient = recipient;
                if (recipient.startsWith("whatsapp:")) {
                    cleanRecipient = recipient.substring("whatsapp:".length());
                }

                PhoneNumber to;
                PhoneNumber from;

                if (isWhatsApp) {
                    to = new PhoneNumber("whatsapp:" + cleanRecipient);
                    String fromNum = twilioProperties.getWhatsappNumber() != null ? twilioProperties.getWhatsappNumber() : twilioProperties.getPhoneNumber();
                    from = new PhoneNumber("whatsapp:" + fromNum);
                } else {
                    to = new PhoneNumber(cleanRecipient);
                    from = new PhoneNumber(twilioProperties.getPhoneNumber());
                }

                log.info("Sending real Twilio {} message to {}. Sender is: {}", 
                         isWhatsApp ? "WhatsApp" : "SMS", to.toString(), from.toString());

                Message twilioMessage = Message.creator(to, from, messageBody).create();
                
                log.info("Notification successfully sent via Twilio. Message SID: {}", twilioMessage.getSid());
                notification.setStatus(Notification.NotificationStatus.SENT);
                
            } catch (TwilioException e) {
                log.error("Failed to send notification via Twilio: {}", e.getMessage(), e);
                notification.setStatus(Notification.NotificationStatus.FAILED);
            }
        } else {
            // Fallback simulation
            if (type == Notification.NotificationType.SMS) {
                log.info("Twilio is not configured. Simulating sending SMS to {}: {}", recipient, maskedMessage);
            } else {
                log.info("Simulating sending {} notification to {}: {}", type, recipient, maskedMessage);
            }
            notification.setStatus(Notification.NotificationStatus.SENT);
        }

        return notificationRepository.save(notification);
    }

    String maskOtp(String message) {
        if (message == null) {
            return null;
        }
        // Mask 4 to 6 digit numeric verification codes
        return message.replaceAll("\\b\\d{4,6}\\b", "******");
    }
}

