package com.voicepay.ivr.service;

import com.voicepay.ivr.dto.CallRequest;
import com.voicepay.ivr.dto.IvrResponse;
import com.voicepay.ivr.dto.LiveCall;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.twilio.twiml.VoiceResponse;
import com.twilio.twiml.voice.Say;
import com.twilio.twiml.voice.Gather;
import com.twilio.twiml.voice.Hangup;
import java.time.LocalDateTime;
import java.util.UUID;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class IvrService {

    private final RestTemplate restTemplate;
    private final LiveCallBroadcaster broadcaster;
    private final Map<String, LiveCall> liveCalls = new java.util.concurrent.ConcurrentHashMap<>();

    @Value("${app.user-service.url}")
    private String userServiceUrl;

    @Value("${app.payment-service.url}")
    private String paymentServiceUrl;

    @SuppressWarnings("unchecked")
    public IvrResponse handleIncomingCall(CallRequest request) {
        log.info("Incoming call from: {}", request.getFrom());
        String callId = java.util.UUID.randomUUID().toString();

        try {
            // Buscamos al usuario por teléfono
            Map<String, Object> user = restTemplate.getForObject(
                    userServiceUrl + "/phone/" + request.getFrom(), Map.class);

            if (user != null) {
                String name = (String) user.get("name");
                Long userId = ((Number) user.get("id")).longValue();

                // Registramos la llamada en vivo
                liveCalls.put(callId, LiveCall.builder()
                        .id(callId)
                        .phoneNumber(request.getFrom())
                        .userName(name)
                        .status("WAITING_CONFIRMATION")
                        .timestamp(java.time.LocalDateTime.now())
                        .build());

                // 📡 Emitir a todos los clientes WebSocket
                broadcaster.broadcast(liveCalls.values());

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

        // Buscamos la llamada activa para este usuario (simplificado)
        LiveCall activeCall = liveCalls.values().stream()
                .filter(c -> c.getStatus().equals("WAITING_CONFIRMATION"))
                .findFirst().orElse(null);

        if (activeCall != null) {
            activeCall.setStatus("PROCESSING_PAYMENT");
            broadcaster.broadcast(liveCalls.values()); // 📡 Estado: procesando
        }

        try {
            Map<String, Object> paymentRequest = new HashMap<>();
            paymentRequest.put("userId", userId);
            paymentRequest.put("amount", 25.0);
            paymentRequest.put("description", "Pago vía IVR");

            restTemplate.postForObject(paymentServiceUrl, paymentRequest, Map.class);

            if (activeCall != null) {
                activeCall.setStatus("COMPLETED");
                broadcaster.broadcast(liveCalls.values()); // 📡 Estado: completado
                // Removemos después de 5 segundos y notificamos de nuevo
                new java.util.Timer().schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        liveCalls.remove(activeCall.getId());
                        broadcaster.broadcast(liveCalls.values()); // 📡 Llamada eliminada
                    }
                }, 5000);
            }

            return IvrResponse.builder()
                    .message("Gracias. Su pago ha sido procesado correctamente. ¡Adiós!")
                    .nextAction("HANGUP")
                    .build();
        } catch (Exception e) {
            log.error("Error processing payment: {}", e.getMessage());
            if (activeCall != null) {
                activeCall.setStatus("FAILED");
                broadcaster.broadcast(liveCalls.values()); // 📡 Estado: fallido
            }
            return IvrResponse.builder()
                    .message("Hubo un error al procesar su pago. Inténtelo de nuevo más tarde.")
                    .nextAction("HANGUP")
                    .build();
        }
    }

    @SuppressWarnings("unchecked")
    public String handleTwilioCall(String from) {
        log.info("Handling real Twilio call from: {}", from);
        String callId = UUID.randomUUID().toString();

        Map<String, Object> user = null;
        try {
            // Buscamos al usuario por teléfono
            user = restTemplate.getForObject(userServiceUrl + "/phone/" + from, Map.class);
        } catch (Exception e) {
            log.error("User service error, checking fallback for: {}", from);
        }

        // PARCHE RICHARD: Si el servicio falla o no lo encuentra
        // Probamos con varias versiones del número por si Twilio lo envía distinto
        if ((user == null || user.get("name") == null) && from != null && 
            (from.contains("642297705") || from.contains("34642297705"))) {
            log.info("Applying manual fallback for Richard from number: {}", from);
            user = new HashMap<>();
            user.put("id", 1);
            user.put("name", "Richard");
        }

        if (user != null) {
            String name = (String) user.get("name");
            Long userId = ((Number) user.get("id")).longValue();

            // Registramos la llamada en vivo en nuestro Dashboard
            liveCalls.put(callId, LiveCall.builder()
                    .id(callId)
                    .phoneNumber(from)
                    .userName(name)
                    .status("WAITING_CONFIRMATION")
                    .timestamp(LocalDateTime.now())
                    .build());

            broadcaster.broadcast(liveCalls.values());

            // Generamos TwiML
            return new VoiceResponse.Builder()
                    .say(new Say.Builder("Hola " + name + ". Bienvenido a VoicePay. Usted tiene un pago pendiente de 25 euros.")
                            .language(Say.Language.ES_ES).build())
                    .gather(new Gather.Builder()
                            .numDigits(1)
                            .action("/ivr/twilio-webhook?userId=" + userId + "&callId=" + callId)
                            .say(new Say.Builder("Para confirmar el pago, pulse 1. Para cancelar, cuelgue.")
                                    .language(Say.Language.ES_ES).build())
                            .build())
                    .build().toXml();
        }

        return new VoiceResponse.Builder()
                .say(new Say.Builder("Lo sentimos, no hemos podido identificar su número. Por favor, póngase en contacto con soporte.")
                        .language(Say.Language.ES_ES).build())
                .hangup(new Hangup.Builder().build())
                .build().toXml();
    }

    public String handleTwilioWebhook(Long userId, String callId, String digits) {
        log.info("Received Twilio webhook: userId={}, digits={}", userId, digits);
        
        if ("1".equals(digits)) {
            log.info("User confirmed payment via DTMF");
            
            // Reutilizamos la lógica de confirmación existente
            confirmPayment(userId);
            
            // Actualizamos el objeto en liveCalls para que el Dashboard lo marque como completado
            LiveCall call = liveCalls.get(callId);
            if (call != null) {
                call.setStatus("COMPLETED");
                broadcaster.broadcast(liveCalls.values());
            }

            return new VoiceResponse.Builder()
                    .say(new Say.Builder("Gracias. Su pago ha sido procesado correctamente. ¡Adiós!")
                            .language(Say.Language.ES_ES).build())
                    .hangup(new Hangup.Builder().build())
                    .build().toXml();
        }

        return new VoiceResponse.Builder()
                .say(new Say.Builder("Operación cancelada. ¡Adiós!")
                        .language(Say.Language.ES_ES).build())
                .hangup(new Hangup.Builder().build())
                .build().toXml();
    }

    public java.util.Collection<LiveCall> getLiveCalls() {
        return liveCalls.values();
    }
}
