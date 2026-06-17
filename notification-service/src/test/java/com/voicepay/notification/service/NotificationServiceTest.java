package com.voicepay.notification.service;

import com.voicepay.notification.config.TwilioProperties;
import com.voicepay.notification.model.Notification;
import com.voicepay.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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
}
