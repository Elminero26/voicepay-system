package com.voicepay.payment.service;

import com.voicepay.payment.client.NotificationServiceClient;
import com.voicepay.payment.client.UserServiceClient;
import com.voicepay.payment.model.Payment;
import com.voicepay.payment.model.Subscription;
import com.voicepay.payment.repository.PaymentRepository;
import com.voicepay.payment.repository.SubscriptionRepository;
import com.voicepay.payment.security.JwtUtil;
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
        List<Subscription> dueSubscriptions = subscriptionRepository.findByStatusAndNextPaymentDateBefore(
                Subscription.SubscriptionStatus.ACTIVE, now);

        log.info("Found {} due subscriptions to process.", dueSubscriptions.size());

        for (Subscription sub : dueSubscriptions) {
            try {
                processSingleRecurringPayment(sub);
            } catch (Exception e) {
                log.error("Error processing recurring payment for subscription ID {}: {}", sub.getId(), e.getMessage());
            }
        }
    }

    private void processSingleRecurringPayment(Subscription sub) {
        log.info("Processing recurring payment for subscription ID: {}, User ID: {}", sub.getId(), sub.getUserId());

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

        if (paymentSuccess) {
            log.info("Recurring payment succeeded for subscription ID: {}", sub.getId());
            payment.setStatus(Payment.PaymentStatus.COMPLETED);
            payment.setTransactionId("TX-SUB-" + System.currentTimeMillis());

            // Actualizar suscripción
            sub.setLastPaymentDate(LocalDateTime.now());
            sub.setNextPaymentDate(calculateNextPaymentDate(sub.getNextPaymentDate(), sub.getPeriodicity()));

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

            // En caso de fallo, para este demo avanzamos el ciclo de pago para no entrar en bucle infinito del scheduler,
            // pero en producción se podría dejar pendiente o reintentar. Avanzamos el pago y notificamos el fallo.
            sub.setNextPaymentDate(calculateNextPaymentDate(sub.getNextPaymentDate(), sub.getPeriodicity()));

            paymentRepository.save(payment);
            subscriptionRepository.save(sub);

            String notifMsg = "ATENCIÓN: El cobro automático de su suscripción por " + sub.getAmount() + " " + sub.getCurrency() + " ha fallado. Por favor, revise sus métodos de pago.";
            if (!"EUR".equalsIgnoreCase(sub.getCurrency())) {
                notifMsg += " (Cobro intentado por " + sub.getAmount() + " " + sub.getCurrency() + ", Equivalente a " + convertedAmount + " EUR)";
            }
            sendNotification(sub, notifMsg);
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
}
