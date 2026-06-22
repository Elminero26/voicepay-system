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
import java.util.ArrayList;
import com.voicepay.notification.dto.DunningNotificationRequest;

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

    public List<Notification> sendDunningNotification(DunningNotificationRequest request) {
        log.info("Processing dunning notification request for event: {} (client: {}, subscription: {})", 
                 request.getEventType(), request.getClientName(), request.getSubscriptionName());

        List<Notification> createdNotifications = new ArrayList<>();

        String emailSubject;
        String emailBody;
        String smsBody;

        if ("SUSPENSION".equalsIgnoreCase(request.getEventType())) {
            emailSubject = "SUSPENSIÓN DE SERVICIO: Suscripción cancelada - VoicePay";
            emailBody = String.format(
                "<h3>Estimado/a %s,</h3>" +
                "<p>Lamentamos informarle que, tras reiterados intentos fallidos de cobro, hemos suspendido definitivamente sus servicios y cancelado la suscripción <strong>%s</strong>.</p>" +
                "<p>El monto adeudado acumulado es de <strong>%s %s</strong>.</p>" +
                "<p>Si desea reactivar su suscripción o tiene alguna consulta, comuníquese con nuestro equipo de soporte.</p>" +
                "<p>Atentamente,<br>El equipo de VoicePay</p>",
                request.getClientName(), request.getSubscriptionName(), request.getAmount(), request.getCurrency()
            );
            smsBody = String.format(
                "VoicePay: Hola %s, tu suscripcion %s ha sido cancelada y los servicios suspendidos debido al impago de %s %s.",
                request.getClientName(), request.getSubscriptionName(), request.getAmount(), request.getCurrency()
            );
        } else { // WARNING or default
            emailSubject = "Advertencia de cobro fallido - VoicePay";
            emailBody = String.format(
                "<h3>Estimado/a %s,</h3>" +
                "<p>Le informamos que el cobro automático de su suscripción <strong>%s</strong> por un monto de <strong>%s %s</strong> ha fallado.</p>" +
                "<p>Su suscripción se encuentra actualmente en estado <strong>PAST_DUE</strong>. Realizaremos reintentos de cobro automáticos en los próximos días.</p>" +
                "<p>Por favor, verifique su método de pago para evitar la suspensión definitiva de sus servicios.</p>" +
                "<p>Atentamente,<br>El equipo de VoicePay</p>",
                request.getClientName(), request.getSubscriptionName(), request.getAmount(), request.getCurrency()
            );
            smsBody = String.format(
                "VoicePay: Hola %s, el pago de %s %s para tu suscripcion %s ha fallado. Evita la suspension regularizando tu metodo de pago.",
                request.getClientName(), request.getAmount(), request.getCurrency(), request.getSubscriptionName()
            );
        }

        // 1. Crear y enviar Email (Simulado)
        Notification emailNotification = Notification.builder()
                .recipient(request.getEmail())
                .message(String.format("Asunto: %s\nCuerpo:\n%s", emailSubject, emailBody))
                .type(Notification.NotificationType.EMAIL)
                .build();
        
        log.info("Simulating sending EMAIL to: {}\nSubject: {}\nBody: {}", 
                 emailNotification.getRecipient(), emailSubject, emailBody);
        emailNotification.setStatus(Notification.NotificationStatus.SENT);
        createdNotifications.add(notificationRepository.save(emailNotification));

        // 2. Crear y enviar SMS
        Notification smsNotification = Notification.builder()
                .recipient(request.getPhoneNumber())
                .message(smsBody)
                .type(Notification.NotificationType.SMS)
                .build();

        createdNotifications.add(sendNotification(smsNotification));

        return createdNotifications;
    }
}

