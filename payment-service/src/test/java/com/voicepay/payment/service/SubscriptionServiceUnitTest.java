package com.voicepay.payment.service;

import com.voicepay.payment.client.NotificationServiceClient;
import com.voicepay.payment.client.UserServiceClient;
import com.voicepay.payment.model.Payment;
import com.voicepay.payment.model.Subscription;
import com.voicepay.payment.repository.PaymentRepository;
import com.voicepay.payment.repository.SubscriptionRepository;
import com.voicepay.payment.security.JwtUtil;
import com.voicepay.payment.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
public class SubscriptionServiceUnitTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGatewaySimulator paymentGatewaySimulator;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private NotificationServiceClient notificationServiceClient;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private CurrencyExchangeService currencyExchangeService;

    @Mock
    private AppProperties appProperties;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private Subscription activeSubscription;

    @BeforeEach
    void setUp() {
        activeSubscription = Subscription.builder()
                .id(1L)
                .userId(10L)
                .amount(new BigDecimal("29.99"))
                .currency("EUR")
                .periodicity(Subscription.Periodicity.MONTHLY)
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .nextPaymentDate(LocalDateTime.now().minusMinutes(5)) // due!
                .description("Test Subscription")
                .build();

        lenient().when(jwtUtil.generateToken(any(), any())).thenReturn("mocked-token");

        // Mock appProperties configuration
        AppProperties.Dunning dunning = new AppProperties.Dunning();
        dunning.setRetryDelaysDays(List.of(1, 3, 7));
        lenient().when(appProperties.getDunning()).thenReturn(dunning);

        // Mock default behavior of currencyExchangeService
        lenient().when(currencyExchangeService.getRate(anyString())).thenReturn(BigDecimal.ONE);
        lenient().when(currencyExchangeService.convert(any(), anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createSubscription_ShouldSucceed_WhenUserIsValid() {
        when(userServiceClient.validateUser(anyLong(), any())).thenReturn(new Object());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArguments()[0]);

        Subscription input = Subscription.builder()
                .userId(10L)
                .amount(new BigDecimal("29.99"))
                .currency("EUR")
                .periodicity(Subscription.Periodicity.MONTHLY)
                .build();

        Subscription result = subscriptionService.createSubscription(input);

        assertNotNull(result);
        assertEquals(Subscription.SubscriptionStatus.ACTIVE, result.getStatus());
        assertNotNull(result.getNextPaymentDate());
        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
    }

    @Test
    void cancelSubscription_ShouldSucceed_WhenSubscriptionIsActive() {
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(activeSubscription));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArguments()[0]);

        Subscription result = subscriptionService.cancelSubscription(1L);

        assertNotNull(result);
        assertEquals(Subscription.SubscriptionStatus.CANCELLED, result.getStatus());
        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
    }

    @Test
    void cancelSubscription_ShouldFail_WhenAlreadyCancelled() {
        activeSubscription.setStatus(Subscription.SubscriptionStatus.CANCELLED);
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(activeSubscription));

        assertThrows(IllegalStateException.class, () -> subscriptionService.cancelSubscription(1L));
    }

    @Test
    void processDueSubscriptions_ShouldChargeSuccessfully_WhenGatewaySucceeds() {
        when(subscriptionRepository.findByStatusAndNextPaymentDateBefore(any(), any()))
                .thenReturn(List.of(activeSubscription));
        when(userServiceClient.validateUser(anyLong(), any())).thenReturn(new Object());
        when(paymentGatewaySimulator.processPayment(any(), any())).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArguments()[0]);
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArguments()[0]);

        subscriptionService.processDueSubscriptions();

        assertEquals(Subscription.SubscriptionStatus.ACTIVE, activeSubscription.getStatus());
        assertNotNull(activeSubscription.getLastPaymentDate());
        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
    }

    @Test
    void processDueSubscriptions_ShouldCancelSubscription_WhenUserIsInvalid() {
        when(subscriptionRepository.findByStatusAndNextPaymentDateBefore(any(), any()))
                .thenReturn(List.of(activeSubscription));
        // Force user validation failure
        when(userServiceClient.validateUser(anyLong(), any())).thenThrow(new RuntimeException("User invalid"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArguments()[0]);
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArguments()[0]);

        subscriptionService.processDueSubscriptions();

        // Should be cancelled since user validation failed
        assertEquals(Subscription.SubscriptionStatus.CANCELLED, activeSubscription.getStatus());
        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
    }

    @Test
    void processDueSubscriptions_ShouldTransitionToPastDue_WhenActiveAttemptFails() {
        when(subscriptionRepository.findByStatusAndNextPaymentDateBefore(any(), any()))
                .thenReturn(List.of(activeSubscription));
        when(userServiceClient.validateUser(anyLong(), any())).thenReturn(new Object());
        // Force gateway failure
        when(paymentGatewaySimulator.processPayment(any(), any())).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArguments()[0]);
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArguments()[0]);

        subscriptionService.processDueSubscriptions();

        assertEquals(Subscription.SubscriptionStatus.PAST_DUE, activeSubscription.getStatus());
        assertEquals(0, activeSubscription.getRetryCount());
        assertNotNull(activeSubscription.getLastAttemptDate());
        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
    }

    @Test
    void processDueSubscriptions_ShouldIncrementRetryCount_WhenPastDueAttemptFails() {
        Subscription pastDueSub = Subscription.builder()
                .id(2L)
                .userId(10L)
                .amount(new BigDecimal("29.99"))
                .currency("EUR")
                .periodicity(Subscription.Periodicity.MONTHLY)
                .status(Subscription.SubscriptionStatus.PAST_DUE)
                .retryCount(0)
                .maxRetries(3)
                .lastAttemptDate(LocalDateTime.now().minusDays(2)) // delay for retryCount 0 is 1 day, so this is due!
                .build();

        when(subscriptionRepository.findByStatusAndNextPaymentDateBefore(any(), any()))
                .thenReturn(List.of());
        when(subscriptionRepository.findByStatus(Subscription.SubscriptionStatus.PAST_DUE))
                .thenReturn(List.of(pastDueSub));
        when(userServiceClient.validateUser(anyLong(), any())).thenReturn(new Object());
        when(paymentGatewaySimulator.processPayment(any(), any())).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArguments()[0]);
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArguments()[0]);

        subscriptionService.processDueSubscriptions();

        assertEquals(Subscription.SubscriptionStatus.PAST_DUE, pastDueSub.getStatus());
        assertEquals(1, pastDueSub.getRetryCount());
        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
    }

    @Test
    void processDueSubscriptions_ShouldTransitionToCancelled_WhenRetryCountExceedsMax() {
        Subscription pastDueSub = Subscription.builder()
                .id(2L)
                .userId(10L)
                .amount(new BigDecimal("29.99"))
                .currency("EUR")
                .periodicity(Subscription.Periodicity.MONTHLY)
                .status(Subscription.SubscriptionStatus.PAST_DUE)
                .retryCount(2)
                .maxRetries(3)
                .lastAttemptDate(LocalDateTime.now().minusDays(8)) // delay for retryCount 2 is 7 days, so due!
                .build();

        when(subscriptionRepository.findByStatusAndNextPaymentDateBefore(any(), any()))
                .thenReturn(List.of());
        when(subscriptionRepository.findByStatus(Subscription.SubscriptionStatus.PAST_DUE))
                .thenReturn(List.of(pastDueSub));
        when(userServiceClient.validateUser(anyLong(), any())).thenReturn(new Object());
        when(paymentGatewaySimulator.processPayment(any(), any())).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArguments()[0]);
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArguments()[0]);

        subscriptionService.processDueSubscriptions();

        // 2 + 1 = 3 retryCount, which is >= maxRetries (3), so CANCELLED
        assertEquals(Subscription.SubscriptionStatus.CANCELLED, pastDueSub.getStatus());
        assertEquals(3, pastDueSub.getRetryCount());
        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
    }

    @Test
    void processDueSubscriptions_ShouldTransitionBackToActive_WhenRetrySucceeds() {
        Subscription pastDueSub = Subscription.builder()
                .id(2L)
                .userId(10L)
                .amount(new BigDecimal("29.99"))
                .currency("EUR")
                .periodicity(Subscription.Periodicity.MONTHLY)
                .status(Subscription.SubscriptionStatus.PAST_DUE)
                .retryCount(1)
                .maxRetries(3)
                .lastAttemptDate(LocalDateTime.now().minusDays(4)) // delay for retryCount 1 is 3 days, so due!
                .build();

        when(subscriptionRepository.findByStatusAndNextPaymentDateBefore(any(), any()))
                .thenReturn(List.of());
        when(subscriptionRepository.findByStatus(Subscription.SubscriptionStatus.PAST_DUE))
                .thenReturn(List.of(pastDueSub));
        when(userServiceClient.validateUser(anyLong(), any())).thenReturn(new Object());
        when(paymentGatewaySimulator.processPayment(any(), any())).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArguments()[0]);
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArguments()[0]);

        subscriptionService.processDueSubscriptions();

        assertEquals(Subscription.SubscriptionStatus.ACTIVE, pastDueSub.getStatus());
        assertEquals(0, pastDueSub.getRetryCount());
        assertNull(pastDueSub.getLastAttemptDate());
        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
    }

    @Test
    void processDueSubscriptions_ShouldFilterOutNotDuePastDueSub() {
        Subscription notDueSub = Subscription.builder()
                .id(3L)
                .userId(10L)
                .amount(new BigDecimal("29.99"))
                .currency("EUR")
                .periodicity(Subscription.Periodicity.MONTHLY)
                .status(Subscription.SubscriptionStatus.PAST_DUE)
                .retryCount(1)
                .maxRetries(3)
                .lastAttemptDate(LocalDateTime.now().minusHours(2)) // delay for retryCount 1 is 3 days, so NOT due!
                .build();

        when(subscriptionRepository.findByStatusAndNextPaymentDateBefore(any(), any()))
                .thenReturn(List.of());
        when(subscriptionRepository.findByStatus(Subscription.SubscriptionStatus.PAST_DUE))
                .thenReturn(List.of(notDueSub));

        subscriptionService.processDueSubscriptions();

        // Should not have processed it since delay is not met
        verify(paymentGatewaySimulator, never()).processPayment(any(), any());
        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }
}
