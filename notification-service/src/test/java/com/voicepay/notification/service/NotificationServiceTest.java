package com.voicepay.notification.service;

import com.voicepay.notification.config.TwilioProperties;
import com.voicepay.notification.model.Notification;
import com.voicepay.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private TwilioProperties twilioProperties;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        twilioProperties = new TwilioProperties();
        twilioProperties.setAccountSid("AC_PLACEHOLDER");
        twilioProperties.setAuthToken("TOKEN_PLACEHOLDER");
        twilioProperties.setPhoneNumber("+10000000000");
        twilioProperties.setWhatsappNumber("+10000000000");
        twilioProperties.setWhatsappEnabled(false);

        notificationService = new NotificationService(notificationRepository, twilioProperties);
    }

    @Test
    void sendNotification_WhenTwilioNotConfigured_ShouldFallbackToSimulationAndSetSent() {
        Notification notification = Notification.builder()
                .recipient("+34600123456")
                .message("Su código OTP es 123456")
                .type(Notification.NotificationType.SMS)
                .build();

        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Notification result = notificationService.sendNotification(notification);

        assertEquals(Notification.NotificationStatus.SENT, result.getStatus());
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    void sendNotification_WhenTypeIsNotSMS_ShouldFallbackToSimulationAndSetSent() {
        Notification notification = Notification.builder()
                .recipient("test@example.com")
                .message("Su pago ha sido procesado")
                .type(Notification.NotificationType.EMAIL)
                .build();

        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Notification result = notificationService.sendNotification(notification);

        assertEquals(Notification.NotificationStatus.SENT, result.getStatus());
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    void maskOtp_ShouldMask4And6DigitCodes() {
        String msg1 = "Su código de verificación es 1234";
        String msg2 = "Su código de verificación es 123456. Expira en 3 minutos.";
        String msg3 = "Su pago de 50.00 EUR con tarjeta terminada en 4321 ha sido exitoso.";
        String msg4 = "Llamar al +34600123456.";

        assertEquals("Su código de verificación es ******", notificationService.maskOtp(msg1));
        assertEquals("Su código de verificación es ******. Expira en 3 minutos.", notificationService.maskOtp(msg2));
        assertEquals("Su pago de 50.00 EUR con tarjeta terminada en ****** ha sido exitoso.", notificationService.maskOtp(msg3));
        assertEquals("Llamar al +34600123456.", notificationService.maskOtp(msg4));
    }

    @Test
    void sendDunningNotification_Warning_ShouldCreateAndSaveEmailAndSms() {
        com.voicepay.notification.dto.DunningNotificationRequest request = com.voicepay.notification.dto.DunningNotificationRequest.builder()
                .clientName("Richard Mateo")
                .email("richard@voicepay.com")
                .phoneNumber("+34600123456")
                .subscriptionName("Plan Premium")
                .amount(new java.math.BigDecimal("29.99"))
                .currency("EUR")
                .eventType("WARNING")
                .build();

        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Notification> results = notificationService.sendDunningNotification(request);

        assertNotNull(results);
        assertEquals(2, results.size());

        Notification email = results.get(0);
        assertEquals("richard@voicepay.com", email.getRecipient());
        assertEquals(Notification.NotificationType.EMAIL, email.getType());
        assertEquals(Notification.NotificationStatus.SENT, email.getStatus());
        assertTrue(email.getMessage().contains("Advertencia de cobro fallido"));
        assertTrue(email.getMessage().contains("Plan Premium"));
        assertTrue(email.getMessage().contains("29.99 EUR"));

        Notification sms = results.get(1);
        assertEquals("+34600123456", sms.getRecipient());
        assertEquals(Notification.NotificationType.SMS, sms.getType());
        assertEquals(Notification.NotificationStatus.SENT, sms.getStatus());
        assertTrue(sms.getMessage().contains("pago de 29.99 EUR"));
        assertTrue(sms.getMessage().contains("Plan Premium"));

        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    @Test
    void sendDunningNotification_Suspension_ShouldCreateAndSaveEmailAndSms() {
        com.voicepay.notification.dto.DunningNotificationRequest request = com.voicepay.notification.dto.DunningNotificationRequest.builder()
                .clientName("Richard Mateo")
                .email("richard@voicepay.com")
                .phoneNumber("+34600123456")
                .subscriptionName("Plan Premium")
                .amount(new java.math.BigDecimal("29.99"))
                .currency("EUR")
                .eventType("SUSPENSION")
                .build();

        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Notification> results = notificationService.sendDunningNotification(request);

        assertNotNull(results);
        assertEquals(2, results.size());

        Notification email = results.get(0);
        assertEquals("richard@voicepay.com", email.getRecipient());
        assertEquals(Notification.NotificationType.EMAIL, email.getType());
        assertEquals(Notification.NotificationStatus.SENT, email.getStatus());
        assertTrue(email.getMessage().contains("SUSPENSIÓN DE SERVICIO"));
        assertTrue(email.getMessage().contains("Plan Premium"));
        assertTrue(email.getMessage().contains("29.99 EUR"));

        Notification sms = results.get(1);
        assertEquals("+34600123456", sms.getRecipient());
        assertEquals(Notification.NotificationType.SMS, sms.getType());
        assertEquals(Notification.NotificationStatus.SENT, sms.getStatus());
        assertTrue(sms.getMessage().contains("suspendidos debido al impago de 29.99 EUR"));
        assertTrue(sms.getMessage().contains("Plan Premium"));

        verify(notificationRepository, times(2)).save(any(Notification.class));
    }
}
