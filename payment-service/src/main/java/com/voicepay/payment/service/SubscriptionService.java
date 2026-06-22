package com.voicepay.payment.service;

import com.voicepay.payment.client.NotificationServiceClient;
import com.voicepay.payment.client.UserServiceClient;
import com.voicepay.payment.model.Payment;
import com.voicepay.payment.model.Subscription;
import com.voicepay.payment.repository.PaymentRepository;
import com.voicepay.payment.repository.SubscriptionRepository;
import com.voicepay.payment.security.JwtUtil;
import com.voicepay.payment.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGatewaySimulator paymentGatewaySimulator;
    private final UserServiceClient userServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final JwtUtil jwtUtil;
    private final CurrencyExchangeService currencyExchangeService;
    private final AppProperties appProperties;

    private HttpHeaders getHeadersWithJwt() {
        HttpHeaders headers = new HttpHeaders();
        String token = jwtUtil.generateToken("payment-service", "ROLE_ADMIN");
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    public List<Subscription> getAllSubscriptions() {
        return subscriptionRepository.findAll();
    }

    public List<Subscription> getSubscriptionsByUserId(Long userId) {
        return subscriptionRepository.findByUserId(userId);
    }

    public Subscription getSubscriptionById(Long id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Subscription not found with id: " + id));
    }

    public Subscription createSubscription(Subscription subscription) {
        log.info("Creating new subscription for user: {}", subscription.getUserId());

        // Validar que el usuario existe en el User Service
        try {
            userServiceClient.validateUser(subscription.getUserId(), getHeadersWithJwt());
        } catch (Exception e) {
            log.error("User validation failed for subscription. User ID: {}", subscription.getUserId());
            throw new RuntimeException("Validation failed: User does not exist or User Service is down.");
        }

        // Si no se provee fecha del próximo pago, se calcula por defecto a partir de ahora
        if (subscription.getNextPaymentDate() == null) {
            subscription.setNextPaymentDate(calculateNextPaymentDate(LocalDateTime.now(), subscription.getPeriodicity()));
        }

        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        Subscription saved = subscriptionRepository.save(subscription);

        // Notificar creación exitosa
        String msg = "Su suscripción recurrente de " + saved.getAmount() + " " + saved.getCurrency() + " (" + saved.getPeriodicity() + ") ha sido activada con éxito.";
        if (!"EUR".equalsIgnoreCase(saved.getCurrency())) {
            BigDecimal converted = currencyExchangeService.convert(saved.getAmount(), saved.getCurrency(), "EUR");
            BigDecimal r = currencyExchangeService.getRate(saved.getCurrency());
            msg += " (Aprox. " + converted + " EUR, Tasa: " + r + ")";
        }
        sendNotification(saved, msg);

        return saved;
    }

    public Subscription cancelSubscription(Long subscriptionId) {
        log.info("Cancelling subscription with ID: {}", subscriptionId);
        Subscription subscription = getSubscriptionById(subscriptionId);

        if (subscription.getStatus() == Subscription.SubscriptionStatus.CANCELLED) {
            throw new IllegalStateException("Subscription is already cancelled.");
        }

        subscription.setStatus(Subscription.SubscriptionStatus.CANCELLED);
        Subscription saved = subscriptionRepository.save(subscription);

        sendNotification(saved, "Su suscripción de " + saved.getAmount() + " " + saved.getCurrency() + " ha sido cancelada.");

        return saved;
    }

    @Transactional
    public void processDueSubscriptions() {
        LocalDateTime now = LocalDateTime.now();
        log.info("Finding due active subscriptions up to: {}", now);
        List<Subscription> dueActiveSubscriptions = subscriptionRepository.findByStatusAndNextPaymentDateBefore(
                Subscription.SubscriptionStatus.ACTIVE, now);

        log.info("Finding past due subscriptions...");
        List<Subscription> pastDueSubscriptions = subscriptionRepository.findByStatus(
                Subscription.SubscriptionStatus.PAST_DUE);

        List<Subscription> duePastDue = pastDueSubscriptions.stream()
                .filter(sub -> isPastDueRetryDue(sub, now))
                .toList();

        log.info("Found {} active due subscriptions and {} past due subscriptions ready for retry.",
                dueActiveSubscriptions.size(), duePastDue.size());

        for (Subscription sub : dueActiveSubscriptions) {
            try {
                processSingleRecurringPayment(sub);
            } catch (Exception e) {
                log.error("Error processing recurring payment for subscription ID {}: {}", sub.getId(), e.getMessage());
            }
        }

        for (Subscription sub : duePastDue) {
            try {
                processSingleRecurringPayment(sub);
            } catch (Exception e) {
                log.error("Error processing retry payment for past due subscription ID {}: {}", sub.getId(), e.getMessage());
            }
        }
    }

    private boolean isPastDueRetryDue(Subscription sub, LocalDateTime now) {
        if (sub.getLastAttemptDate() == null) {
            return true;
        }
        int retryCount = sub.getRetryCount() != null ? sub.getRetryCount() : 0;
        int delayDays = getRetryDelayDays(retryCount);
        LocalDateTime nextRetryDate = sub.getLastAttemptDate().plusDays(delayDays);
        return nextRetryDate.isBefore(now) || nextRetryDate.isEqual(now);
    }

    private int getRetryDelayDays(int retryCount) {
        if (appProperties == null || appProperties.getDunning() == null || appProperties.getDunning().getRetryDelaysDays() == null) {
            return (int) Math.pow(2, retryCount + 1) - 1;
        }
        List<Integer> delays = appProperties.getDunning().getRetryDelaysDays();
        if (retryCount < 0) {
            return 1;
        }
        if (retryCount < delays.size()) {
            return delays.get(retryCount);
        }
        return (int) Math.pow(2, retryCount + 1) - 1;
    }

    private void processSingleRecurringPayment(Subscription sub) {
        log.info("Processing recurring payment for subscription ID: {}, User ID: {}, Status: {}", 
                sub.getId(), sub.getUserId(), sub.getStatus());

        Subscription.SubscriptionStatus originalStatus = sub.getStatus();

        // 1. Validar cobro automático (validar si el usuario sigue existiendo y es válido)
        boolean userValid = false;
        try {
            userServiceClient.validateUser(sub.getUserId(), getHeadersWithJwt());
            userValid = true;
        } catch (Exception e) {
            log.error("Automatic charge validation failed: user ID {} is no longer valid or user service is unreachable.", sub.getUserId());
        }

        if (!userValid) {
            // Si el usuario no es válido, cancelamos la suscripción para evitar cobros futuros infinitos
            sub.setStatus(Subscription.SubscriptionStatus.CANCELLED);
            subscriptionRepository.save(sub);

            // Guardamos registro de pago fallido
            BigDecimal rate = currencyExchangeService.getRate(sub.getCurrency());
            BigDecimal convertedAmount = currencyExchangeService.convert(sub.getAmount(), sub.getCurrency(), "EUR");
            Payment failedPayment = Payment.builder()
                    .userId(sub.getUserId())
                    .amount(sub.getAmount())
                    .currency(sub.getCurrency())
                    .exchangeRate(rate)
                    .convertedAmount(convertedAmount)
                    .description("Cobro automático fallido: Validación de usuario falló")
                    .status(Payment.PaymentStatus.FAILED)
                    .transactionId("TX-SUB-FAIL-USER-" + System.currentTimeMillis())
                    .build();
            paymentRepository.save(failedPayment);
            return;
        }

        // 2. Procesar pago en la pasarela
        boolean paymentSuccess = paymentGatewaySimulator.processPayment(sub.getAmount(), sub.getCurrency());

        BigDecimal rate = currencyExchangeService.getRate(sub.getCurrency());
        BigDecimal convertedAmount = currencyExchangeService.convert(sub.getAmount(), sub.getCurrency(), "EUR");

        Payment payment = Payment.builder()
                .userId(sub.getUserId())
                .amount(sub.getAmount())
                .currency(sub.getCurrency())
                .exchangeRate(rate)
                .convertedAmount(convertedAmount)
                .description(sub.getDescription() != null ? sub.getDescription() : "Cobro periódico de suscripción")
                .build();

        LocalDateTime now = LocalDateTime.now();

        if (paymentSuccess) {
            log.info("Recurring payment succeeded for subscription ID: {}", sub.getId());
            payment.setStatus(Payment.PaymentStatus.COMPLETED);
            payment.setTransactionId("TX-SUB-" + System.currentTimeMillis());

            // Actualizar suscripción
            sub.setStatus(Subscription.SubscriptionStatus.ACTIVE);
            sub.setRetryCount(0);
            sub.setLastAttemptDate(null);
            sub.setLastPaymentDate(now);
            
            // Si la suscripción estaba PAST_DUE, se avanza la fecha del próximo pago a partir del momento del cobro exitoso (now).
            // Si era ACTIVE, se avanza a partir de la fecha del próximo pago planificada para no desfasar el ciclo de facturación.
            LocalDateTime baseDate = (originalStatus == Subscription.SubscriptionStatus.PAST_DUE) ? now : sub.getNextPaymentDate();
            sub.setNextPaymentDate(calculateNextPaymentDate(baseDate, sub.getPeriodicity()));

            paymentRepository.save(payment);
            subscriptionRepository.save(sub);

            String notifMsg = "Cobro automático realizado con éxito. Se cargaron " + sub.getAmount() + " " + sub.getCurrency() + " a su cuenta por su suscripción activa.";
            if (!"EUR".equalsIgnoreCase(sub.getCurrency())) {
                notifMsg += " (Equivalente a " + convertedAmount + " EUR, Tasa: " + rate + ")";
            }
            sendNotification(sub, notifMsg);
        } else {
            log.warn("Recurring payment failed for subscription ID: {}", sub.getId());
            payment.setStatus(Payment.PaymentStatus.FAILED);
            payment.setTransactionId("TX-SUB-FAILED-" + System.currentTimeMillis());

            paymentRepository.save(payment);

            int currentRetryCount = sub.getRetryCount() != null ? sub.getRetryCount() : 0;
            int maxRetries = sub.getMaxRetries() != null ? sub.getMaxRetries() : 3;

            if (originalStatus == Subscription.SubscriptionStatus.ACTIVE) {
                // Primer fallo: pasa a PAST_DUE
                sub.setStatus(Subscription.SubscriptionStatus.PAST_DUE);
                sub.setRetryCount(0); // 0 reintentos previos realizados
                sub.setLastAttemptDate(now);
                subscriptionRepository.save(sub);

                sendDunningNotification(sub, "WARNING");
            } else if (originalStatus == Subscription.SubscriptionStatus.PAST_DUE) {
                // Reintento fallido
                int newRetryCount = currentRetryCount + 1;
                sub.setRetryCount(newRetryCount);
                sub.setLastAttemptDate(now);

                if (newRetryCount >= maxRetries) {
                    sub.setStatus(Subscription.SubscriptionStatus.CANCELLED);
                    subscriptionRepository.save(sub);

                    sendDunningNotification(sub, "SUSPENSION");
                } else {
                    subscriptionRepository.save(sub);

                    sendDunningNotification(sub, "WARNING");
                }
            }
        }
    }

    private LocalDateTime calculateNextPaymentDate(LocalDateTime baseDate, Subscription.Periodicity periodicity) {
        switch (periodicity) {
            case DAILY:
                return baseDate.plusDays(1);
            case WEEKLY:
                return baseDate.plusWeeks(1);
            case YEARLY:
                return baseDate.plusYears(1);
            case MONTHLY:
            default:
                return baseDate.plusMonths(1);
        }
    }

    private void sendNotification(Subscription sub, String message) {
        try {
            String userName = "Usuario";
            try {
                Map<String, Object> userResponse = userServiceClient.getUserDetails(sub.getUserId(), getHeadersWithJwt());
                if (userResponse != null && userResponse.get("name") != null) {
                    userName = userResponse.get("name").toString();
                }
            } catch (Exception e) {
                log.warn("Could not retrieve user name from User Service: {}", e.getMessage());
            }

            HttpHeaders headers = getHeadersWithJwt();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> notificationRequest = new HashMap<>();
            notificationRequest.put("recipient", userName);
            notificationRequest.put("message", "Hola " + userName + ", " + message);
            notificationRequest.put("type", "PUSH");

            notificationServiceClient.sendNotification(notificationRequest, headers);
        } catch (Exception e) {
            log.error("Error sending subscription notification: {}", e.getMessage());
        }
    }

    private void sendDunningNotification(Subscription sub, String eventType) {
        try {
            String userName = "Usuario";
            String email = "correo_simulado@voicepay.com";
            String phoneNumber = "+34600123456";
            try {
                Map<String, Object> userResponse = userServiceClient.getUserDetails(sub.getUserId(), getHeadersWithJwt());
                if (userResponse != null) {
                    if (userResponse.get("name") != null) {
                        userName = userResponse.get("name").toString();
                    }
                    if (userResponse.get("email") != null) {
                        email = userResponse.get("email").toString();
                    }
                    if (userResponse.get("phoneNumber") != null) {
                        phoneNumber = userResponse.get("phoneNumber").toString();
                    }
                }
            } catch (Exception e) {
                log.warn("Could not retrieve user details from User Service: {}", e.getMessage());
            }

            HttpHeaders headers = getHeadersWithJwt();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> dunningRequest = new HashMap<>();
            dunningRequest.put("clientName", userName);
            dunningRequest.put("email", email);
            dunningRequest.put("phoneNumber", phoneNumber);
            dunningRequest.put("subscriptionName", sub.getDescription() != null ? sub.getDescription() : "Suscripción Recurrente");
            dunningRequest.put("amount", sub.getAmount());
            dunningRequest.put("currency", sub.getCurrency());
            dunningRequest.put("eventType", eventType);

            log.info("Sending dunning event of type {} for subscription ID {}", eventType, sub.getId());
            notificationServiceClient.sendDunningNotification(dunningRequest, headers);
        } catch (Exception e) {
            log.error("Error sending dunning notification: {}", e.getMessage());
        }
    }
}
