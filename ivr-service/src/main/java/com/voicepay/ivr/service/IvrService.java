package com.voicepay.ivr.service;

import com.voicepay.ivr.dto.CallRequest;
import com.voicepay.ivr.dto.IvrResponse;
import com.voicepay.ivr.dto.LiveCall;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.twilio.twiml.VoiceResponse;
import com.twilio.twiml.voice.Say;
import com.twilio.twiml.voice.Gather;
import com.twilio.twiml.voice.Hangup;
import java.time.LocalDateTime;
import java.util.UUID;

import java.math.BigDecimal;
import java.util.Map;

import com.voicepay.ivr.client.UserServiceClient;
import com.voicepay.ivr.client.PaymentServiceClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class IvrService {

    private final UserServiceClient userServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final LiveCallBroadcaster broadcaster;
    private final com.voicepay.ivr.repository.LiveCallRepository callRepository;
    private final Map<String, LiveCall> liveCalls = new java.util.concurrent.ConcurrentHashMap<>();

    private final com.voicepay.ivr.security.JwtUtil jwtUtil;

    private org.springframework.http.HttpHeaders getHeadersWithJwt() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        String token = jwtUtil.generateToken("ivr-service", "ROLE_ADMIN");
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    public IvrResponse handleIncomingCall(CallRequest request) {
        log.info("Incoming call simulation from: {}", request.getFrom());
        String callId = "SIM-" + java.util.UUID.randomUUID().toString().substring(0, 8);

        try {
            log.info("Requesting user info for: {}", request.getFrom());
            java.util.List<String> currentEvents = new java.util.ArrayList<>();
            currentEvents.add("Llamada recibida de " + request.getFrom());
            currentEvents.add("Consultando User Service para identificar número...");
            
            Map<String, Object> user = userServiceClient.getUserByPhone(request.getFrom(), getHeadersWithJwt());


            if (user != null) {
                String name = (String) user.get("name");
                Long userId = ((Number) user.get("id")).longValue();
                log.info("User identified for live call: {} (ID: {})", name, userId);
                currentEvents.add("Usuario identificado: " + name + " (ID: " + userId + ")");
                currentEvents.add("Consultando Payment Service para deudas pendientes...");


                // Buscamos el pago pendiente real
                BigDecimal amount = BigDecimal.valueOf(25.00); // Valor de seguridad
                try {
                    java.util.Map<String, Object> paymentData = paymentServiceClient.getPendingPayment(userId, getHeadersWithJwt());
                    if (paymentData != null && paymentData.get("amount") != null) {
                        Object amtObj = paymentData.get("amount");
                        amount = new BigDecimal(amtObj.toString());
                        log.info("Found pending payment of {} for user {}", amount, name);
                        currentEvents.add("Deuda encontrada: " + amount + "€");
                    }

                } catch (Exception e) {
                    log.error("Could not find real payment for user {}, using fallback $25.00. Error: {}", name, e.getMessage());
                }

                // Registramos la llamada en vivo
                LiveCall call = LiveCall.builder()
                        .id(callId)
                        .phoneNumber(request.getFrom())
                        .userName(name)
                        .callAmount(amount.doubleValue()) // <--- CAMBIO DE NOMBRE
                        .status("WAITING_CONFIRMATION")
                        .direction("INBOUND")
                        .timestamp(java.time.LocalDateTime.now())
                        .callEvents(currentEvents)
                        .build();

                
                log.info("Registering LiveCall in dashboard: {} with amount {}", callId, call.getCallAmount());
                liveCalls.put(callId, call);
                callRepository.save(call); // 💾 Guardamos en PostgreSQL
                broadcaster.broadcast(liveCalls.values());

                return IvrResponse.builder()
                        .message("Bienvenido " + name + ". Usted tiene un pago pendiente de " + amount + " euros. Pulse 1 para pagar, o pulse 2 para hablar con un agente.")
                        .nextAction("WAIT_FOR_INPUT")
                        .userId(userId)
                        .build();
            }
        } catch (Exception e) {
            log.error("User identification failed: {}", e.getMessage());
        }

        return IvrResponse.builder()
                .message("Lo sentimos, no hemos podido identificar su número. Por favor, póngase en contacto con soporte.")
                .nextAction("HANGUP")
                .build();
    }

    public IvrResponse confirmPayment(Long userId) {
        return processUserOption(userId, "1");
    }

    public IvrResponse processUserOption(Long userId, String digits) {
        log.info("Processing option {} for user: {}", digits, userId);

        LiveCall activeCall = liveCalls.values().stream()
                .filter(c -> c.getStatus().equals("WAITING_CONFIRMATION") || c.getStatus().equals("PROCESSING_PAYMENT"))
                .findFirst().orElse(null);

        if ("2".equals(digits)) {
            if (activeCall != null) {
                activeCall.setStatus("TRANSFERRED");
                activeCall.setSelectedOption("2");
                activeCall.getCallEvents().add("Usuario pulsó 2: Transfiriendo a un agente humano...");
                callRepository.save(activeCall);
                broadcaster.broadcast(liveCalls.values());
            }
            return IvrResponse.builder()
                    .message("Un momento, por favor. Le estamos transfiriendo con el próximo agente disponible.")
                    .nextAction("TRANSFER")
                    .build();
        } else if ("1".equals(digits)) {
            if (activeCall != null) {
                activeCall.setStatus("PROCESSING_PAYMENT");
                activeCall.setSelectedOption(digits);
                activeCall.getCallEvents().add("Usuario pulsó " + digits + ": Iniciando confirmación de pago...");
                activeCall.getCallEvents().add("Llamando a Payment Service (/confirm)...");
                callRepository.save(activeCall);
                broadcaster.broadcast(liveCalls.values());
            }
        }

        try {
            // Llamamos al nuevo endpoint de confirmación en el Payment Service con seguridad
            paymentServiceClient.confirmPayment(userId, getHeadersWithJwt());

            if (activeCall != null) {
                activeCall.getCallEvents().add("Pago confirmado con éxito en pasarela.");
                activeCall.getCallEvents().add("Llamando a Notification Service para enviar SMS/Push...");
                activeCall.setStatus("COMPLETED");

                activeCall.setSelectedOption("1"); // Opción de pago
                activeCall.setDuration(java.time.Duration.between(activeCall.getTimestamp(), java.time.LocalDateTime.now()).getSeconds());
                activeCall.setDirection("INBOUND");
                callRepository.save(activeCall); // 💾 Guardamos éxito con detalles en DB
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
                    .message("Gracias. Su pago ha sido procesado correctamente. Le hemos enviado un mensaje de confirmación a su móvil. ¡Adiós!")
                    .nextAction("HANGUP")
                    .build();
        } catch (Exception e) {
            log.error("Error processing payment: {}", e.getMessage());
            if (activeCall != null) {
                activeCall.setStatus("FAILED");
                callRepository.save(activeCall); // 💾 Guardamos fallo en DB
                broadcaster.broadcast(liveCalls.values()); // 📡 Estado: fallido
                // Limpieza automática tras fallo
                new java.util.Timer().schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        liveCalls.remove(activeCall.getId());
                        broadcaster.broadcast(liveCalls.values());
                    }
                }, 5000);
            }
            return IvrResponse.builder()
                    .message("Hubo un error al procesar su pago. Inténtelo de nuevo más tarde.")
                    .nextAction("HANGUP")
                    .build();
        }
    }

    public String handleTwilioCall(String from) {
        log.info("Handling real Twilio call from: {}", from);
        String callId = UUID.randomUUID().toString();

        Map<String, Object> user = null;
        try {
            user = userServiceClient.getUserByPhone(from, getHeadersWithJwt());
        } catch (Exception e) {
            log.error("User service error, checking fallback for: {}", from);
        }



        if (user != null) {
            String name = (String) user.get("name");
            Long userId = ((Number) user.get("id")).longValue();

            // Buscamos el pago pendiente real
            String amountStr = "25"; 
            try {
                Map<String, Object> pendingPayment = paymentServiceClient.getPendingPayment(userId, getHeadersWithJwt());
                if (pendingPayment != null && pendingPayment.get("amount") != null) {
                    amountStr = pendingPayment.get("amount").toString();
                }
            } catch (Exception e) {
                log.warn("No pending payment found for user {}, using fallback", userId);
            }

            // Registramos la llamada en vivo en nuestro Dashboard
            liveCalls.put(callId, LiveCall.builder()
                    .id(callId)
                    .phoneNumber(from)
                    .userName(name)
                    .callAmount(Double.parseDouble(amountStr))
                    .status("WAITING_CONFIRMATION")
                    .timestamp(LocalDateTime.now())
                    .build());

            broadcaster.broadcast(liveCalls.values());

            // Generamos TwiML
            return new VoiceResponse.Builder()
                    .say(new Say.Builder("Hola " + name + ". Bienvenido a VoicePay. Usted tiene un pago pendiente de " + amountStr + " euros.")
                            .language(Say.Language.ES_ES).build())
                    .gather(new Gather.Builder()
                            .numDigits(1)
                            .action("/ivr/twilio-webhook?userId=" + userId + "&callId=" + callId)
                            .say(new Say.Builder("Para confirmar el pago, pulse 1. Para hablar con un agente, pulse 2. Para cancelar, cuelgue.")
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
        
        if ("1".equals(digits) || "2".equals(digits)) {
            log.info("User selected option {} via DTMF", digits);
            
            // Reutilizamos la lógica de confirmación existente
            processUserOption(userId, digits);
            
            // Si fue transferencia, devolvemos TwiML de transferencia (simulado con mensaje)
            if ("2".equals(digits)) {
                return new VoiceResponse.Builder()
                        .say(new Say.Builder("Un momento, por favor. Transfiriendo...")
                                .language(Say.Language.ES_ES).build())
                        .hangup(new Hangup.Builder().build()) // En Twilio real aquí iría un <Dial>
                        .build().toXml();
            }
            
            // Actualizamos el objeto en liveCalls para que el Dashboard lo marque como completado
            LiveCall call = liveCalls.get(callId);
            if (call != null) {
                call.setStatus("COMPLETED");
                broadcaster.broadcast(liveCalls.values());
            }

            return new VoiceResponse.Builder()
                    .say(new Say.Builder("Gracias. Su pago ha sido procesado correctamente. Le hemos enviado un mensaje de confirmación a su móvil. ¡Adiós!")
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

    public java.util.List<LiveCall> getCallHistory() {
        return callRepository.findAll();
    }

    public java.util.Collection<LiveCall> getLiveCalls() {
        return liveCalls.values();
    }
}
