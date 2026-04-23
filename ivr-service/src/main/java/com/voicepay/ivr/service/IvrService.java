package com.voicepay.ivr.service;

import com.voicepay.ivr.dto.CallRequest;
import com.voicepay.ivr.dto.IvrResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class IvrService {

    private final RestTemplate restTemplate;

    @Value("${app.user-service.url}")
    private String userServiceUrl;

    @Value("${app.payment-service.url}")
    private String paymentServiceUrl;

    public IvrResponse handleIncomingCall(CallRequest request) {
        log.info("Incoming call from: {}", request.getFrom());

        try {
            // Buscamos al usuario por teléfono
            Map<String, Object> user = restTemplate.getForObject(
                    userServiceUrl + "/phone/" + request.getFrom(), Map.class);

            if (user != null) {
                String name = (String) user.get("name");
                Long userId = ((Number) user.get("id")).longValue();

                return IvrResponse.builder()
                        .message("Bienvenido " + name + ". Usted tiene un pago pendiente. Pulse 1 para pagar 25 euros.")
                        .nextAction("WAIT_FOR_CONFIRMATION")
                        .userId(userId)
                        .build();
            }
        } catch (Exception e) {
            log.error("User not found or error calling User Service: {}", e.getMessage());
        }

        return IvrResponse.builder()
                .message("Lo sentimos, no hemos podido identificar su número. Por favor, póngase en contacto con soporte.")
                .nextAction("HANGUP")
                .build();
    }

    public IvrResponse confirmPayment(Long userId) {
        log.info("Confirming payment for user: {}", userId);

        try {
            Map<String, Object> paymentRequest = new HashMap<>();
            paymentRequest.put("userId", userId);
            paymentRequest.put("amount", 25.0);
            paymentRequest.put("description", "Pago vía IVR");

            restTemplate.postForObject(paymentServiceUrl, paymentRequest, Map.class);

            return IvrResponse.builder()
                    .message("Gracias. Su pago ha sido procesado correctamente. ¡Adiós!")
                    .nextAction("HANGUP")
                    .build();
        } catch (Exception e) {
            log.error("Error processing payment: {}", e.getMessage());
            return IvrResponse.builder()
                    .message("Hubo un error al procesar su pago. Inténtelo de nuevo más tarde.")
                    .nextAction("HANGUP")
                    .build();
        }
    }
}
