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
@SuppressWarnings("null")
public class IvrService {

    private final UserServiceClient userServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final LiveCallBroadcaster broadcaster;
    private final com.voicepay.ivr.repository.LiveCallRepository callRepository;
    private final com.voicepay.ivr.config.TwilioProperties twilioProperties;
    private final Map<String, LiveCall> liveCalls = new java.util.concurrent.ConcurrentHashMap<>();

    private final com.voicepay.ivr.security.JwtUtil jwtUtil;
    
    private final java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newScheduledThreadPool(1);

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
        return processUserOptionWithCallId(userId, digits, null);
    }

    public IvrResponse processUserOptionWithCallId(Long userId, String digits, String callId) {
        log.info("Processing option {} for user: {} (callId: {})", digits, userId, callId);

        LiveCall activeCall = null;
        if (callId != null) {
            activeCall = liveCalls.get(callId);
        }
        
        if (activeCall == null) {
            activeCall = liveCalls.values().stream()
                    .filter(c -> c.getStatus().equals("WAITING_CONFIRMATION") || c.getStatus().equals("PROCESSING_PAYMENT"))
                    .findFirst().orElse(null);
        }

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
                activeCall.setDirection(activeCall.getDirection() != null ? activeCall.getDirection() : "INBOUND");
                callRepository.save(activeCall); // 💾 Guardamos éxito con detalles en DB
                broadcaster.broadcast(liveCalls.values()); // 📡 Estado: completado
                
                // Removemos después de 5 segundos y notificamos de nuevo
                final String finalCallId = activeCall.getId();
                new java.util.Timer().schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        liveCalls.remove(finalCallId);
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
                activeCall.getCallEvents().add("Error al procesar el pago: " + e.getMessage());
                callRepository.save(activeCall); // 💾 Guardamos fallo en DB
                broadcaster.broadcast(liveCalls.values()); // 📡 Estado: fallido
                
                // Limpieza automática tras fallo
                final String finalCallId = activeCall.getId();
                new java.util.Timer().schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        liveCalls.remove(finalCallId);
                        broadcaster.broadcast(liveCalls.values());
                    }
                }, 5000);
            }
            return IvrResponse.builder()
                    .message("Hubo un error al procesar su pago. Inténtelo de nuevo más tarde o póngase en contacto con soporte.")
                    .nextAction("HANGUP")
                    .build();
        }
    }

    public String handleTwilioCall(String from) {
        return handleTwilioCall(from, UUID.randomUUID().toString());
    }

    public String handleTwilioCall(String from, String callSid) {
        log.info("Handling real Twilio call from: {}, callSid: {}", from, callSid);

        java.util.List<String> currentEvents = new java.util.ArrayList<>();
        currentEvents.add("Llamada real entrante de Twilio. SID: " + callSid);
        currentEvents.add("Consultando User Service para identificar número...");

        Map<String, Object> user = null;
        try {
            user = userServiceClient.getUserByPhone(from, getHeadersWithJwt());
        } catch (Exception e) {
            log.error("User service error, checking fallback for: {}", from);
            currentEvents.add("Error al consultar User Service: " + e.getMessage());
        }

        if (user != null) {
            String name = (String) user.get("name");
            Long userId = ((Number) user.get("id")).longValue();
            
            currentEvents.add("Usuario identificado: " + name + " (ID: " + userId + ")");
            currentEvents.add("Consultando Payment Service para deudas pendientes...");

            // Buscamos el pago pendiente real
            String amountStr = "25"; 
            try {
                Map<String, Object> pendingPayment = paymentServiceClient.getPendingPayment(userId, getHeadersWithJwt());
                if (pendingPayment != null && pendingPayment.get("amount") != null) {
                    amountStr = pendingPayment.get("amount").toString();
                    currentEvents.add("Deuda encontrada: " + amountStr + "€");
                } else {
                    currentEvents.add("No se encontraron deudas, usando fallback de 25€.");
                }
            } catch (Exception e) {
                log.warn("No pending payment found for user {}, using fallback", userId);
                currentEvents.add("Error al buscar deuda. Usando fallback de 25€.");
            }

            // Registramos la llamada en vivo en nuestro Dashboard y DB
            LiveCall call = LiveCall.builder()
                    .id(callSid)
                    .phoneNumber(from)
                    .userName(name)
                    .callAmount(Double.parseDouble(amountStr))
                    .status("WAITING_CONFIRMATION")
                    .direction("INBOUND")
                    .timestamp(LocalDateTime.now())
                    .callEvents(currentEvents)
                    .build();

            liveCalls.put(callSid, call);
            callRepository.save(call);
            broadcaster.broadcast(liveCalls.values());

            // Generamos TwiML
            return new VoiceResponse.Builder()
                    .say(new Say.Builder("Hola " + name + ". Bienvenido a Voice Pay. Usted tiene un pago pendiente de " + amountStr + " euros.")
                            .language(Say.Language.ES_ES).build())
                    .gather(new Gather.Builder()
                            .numDigits(1)
                            .action("/ivr/twilio-webhook?userId=" + userId + "&callId=" + callSid)
                            .say(new Say.Builder("Para confirmar el pago, pulse 1. Para hablar con un agente, pulse 2. Para cancelar, cuelgue.")
                                    .language(Say.Language.ES_ES).build())
                            .build())
                    .build().toXml();
        }

        currentEvents.add("No se pudo identificar el número telefónico.");
        
        LiveCall call = LiveCall.builder()
                .id(callSid)
                .phoneNumber(from)
                .userName("Desconocido")
                .callAmount(0.0)
                .status("FAILED")
                .direction("INBOUND")
                .timestamp(LocalDateTime.now())
                .callEvents(currentEvents)
                .build();
        liveCalls.put(callSid, call);
        callRepository.save(call);
        broadcaster.broadcast(liveCalls.values());

        return new VoiceResponse.Builder()
                .say(new Say.Builder("Lo sentimos, no hemos podido identificar su número. Por favor, póngase en contacto con soporte.")
                        .language(Say.Language.ES_ES).build())
                .hangup(new Hangup.Builder().build())
                .build().toXml();
    }

    public String handleTwilioWebhook(Long userId, String callId, String digits) {
        log.info("Received Twilio webhook: userId={}, digits={}, callId={}", userId, digits, callId);
        
        if ("1".equals(digits) || "2".equals(digits)) {
            log.info("User selected option {} via DTMF", digits);
            
            // Reutilizamos la lógica de confirmación existente
            IvrResponse response = processUserOptionWithCallId(userId, digits, callId);
            
            // Si fue transferencia, devolvemos TwiML de transferencia (simulado con mensaje)
            if ("2".equals(digits)) {
                return new VoiceResponse.Builder()
                        .say(new Say.Builder(response.getMessage())
                                .language(Say.Language.ES_ES).build())
                        .hangup(new Hangup.Builder().build()) // En Twilio real aquí iría un <Dial>
                        .build().toXml();
            }
            
            return new VoiceResponse.Builder()
                    .say(new Say.Builder(response.getMessage())
                            .language(Say.Language.ES_ES).build())
                    .hangup(new Hangup.Builder().build())
                    .build().toXml();
        }

        LiveCall call = liveCalls.get(callId);
        if (call != null) {
            call.setStatus("FAILED");
            call.getCallEvents().add("Usuario seleccionó una opción inválida DTMF: " + digits);
            callRepository.save(call);
            broadcaster.broadcast(liveCalls.values());
        }

        return new VoiceResponse.Builder()
                .say(new Say.Builder("Opción inválida. Operación cancelada. ¡Adiós!")
                        .language(Say.Language.ES_ES).build())
                .hangup(new Hangup.Builder().build())
                .build().toXml();
    }

    public void handleTwilioStatus(String callSid, String callStatus, String durationStr) {
        log.info("Received Twilio status callback: callSid={}, status={}, duration={}", callSid, callStatus, durationStr);
        
        LiveCall call = liveCalls.get(callSid);
        if (call == null) {
            call = callRepository.findById(callSid).orElse(null);
        }
        
        if (call != null) {
            call.getCallEvents().add("Cambio de estado Twilio: " + callStatus);
            
            if (durationStr != null && !durationStr.isEmpty()) {
                try {
                    call.setDuration(Long.parseLong(durationStr));
                } catch (NumberFormatException e) {
                    log.warn("Invalid duration format: {}", durationStr);
                }
            }
            
            if ("completed".equalsIgnoreCase(callStatus)) {
                if (!"COMPLETED".equals(call.getStatus()) && !"TRANSFERRED".equals(call.getStatus()) && !"FAILED".equals(call.getStatus())) {
                    call.setStatus("FAILED");
                    call.getCallEvents().add("La llamada finalizó sin confirmación del usuario.");
                }
            } else if ("failed".equalsIgnoreCase(callStatus)) {
                call.setStatus("FAILED");
                call.getCallEvents().add("Error de conexión de Twilio.");
            } else if ("busy".equalsIgnoreCase(callStatus)) {
                call.setStatus("FAILED");
                call.getCallEvents().add("Línea ocupada.");
            } else if ("no-answer".equalsIgnoreCase(callStatus)) {
                call.setStatus("FAILED");
                call.getCallEvents().add("Sin respuesta del usuario.");
            } else if ("canceled".equalsIgnoreCase(callStatus)) {
                call.setStatus("FAILED");
                call.getCallEvents().add("Llamada cancelada de forma externa.");
            } else if ("in-progress".equalsIgnoreCase(callStatus)) {
                if ("PENDING".equals(call.getStatus())) {
                    call.setStatus("WAITING_CONFIRMATION");
                }
            }
            
            callRepository.save(call);
            broadcaster.broadcast(liveCalls.values());
            
            if ("completed".equalsIgnoreCase(callStatus) || "failed".equalsIgnoreCase(callStatus) || 
                "busy".equalsIgnoreCase(callStatus) || "no-answer".equalsIgnoreCase(callStatus) || 
                "canceled".equalsIgnoreCase(callStatus)) {
                
                final String sid = callSid;
                new java.util.Timer().schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        liveCalls.remove(sid);
                        broadcaster.broadcast(liveCalls.values());
                    }
                }, 5000);
            }
        }
    }

    public IvrResponse triggerOutboundCall(String toPhoneNumber, boolean forceMock) {
        log.info("Triggering outbound call to: {}, forceMock={}", toPhoneNumber, forceMock);

        boolean isTwilioConfigured = twilioProperties.getAccountSid() != null 
                && !twilioProperties.getAccountSid().contains("PLACEHOLDER")
                && twilioProperties.getAuthToken() != null 
                && !twilioProperties.getAuthToken().contains("PLACEHOLDER");

        Map<String, Object> user = null;
        try {
            user = userServiceClient.getUserByPhone(toPhoneNumber, getHeadersWithJwt());
        } catch (Exception e) {
            log.error("User service error, checking fallback for outbound: {}", toPhoneNumber);
        }

        String name = user != null ? (String) user.get("name") : "Cliente VoicePay";
        Long userId = user != null ? ((Number) user.get("id")).longValue() : 999L;
        
        double amount = 25.0;
        try {
            Map<String, Object> pendingPayment = paymentServiceClient.getPendingPayment(userId, getHeadersWithJwt());
            if (pendingPayment != null && pendingPayment.get("amount") != null) {
                amount = Double.parseDouble(pendingPayment.get("amount").toString());
            }
        } catch (Exception e) {
            log.warn("No pending payment found for user {}, using fallback", userId);
        }

        final double finalAmount = amount;
        final String finalName = name;
        final Long finalUserId = userId;

        if (isTwilioConfigured && !forceMock) {
            try {
                String callSid = "TW-OUT-" + UUID.randomUUID().toString().substring(0, 8);
                
                String domain = twilioProperties.getWebhookUrl();
                if (domain == null || domain.isEmpty()) {
                    domain = "http://localhost:8082";
                }
                if (domain.endsWith("/")) {
                    domain = domain.substring(0, domain.length() - 1);
                }
                
                String twimlUrl = domain + "/ivr/twilio-call?From=" + java.net.URLEncoder.encode(toPhoneNumber, "UTF-8") 
                        + "&CallSid=" + callSid;
                String statusCallbackUrl = domain + "/ivr/twilio-status";

                log.info("Creating real Twilio call with TwiML URL: {}", twimlUrl);
                
                com.twilio.rest.api.v2010.account.Call twilioCall = com.twilio.rest.api.v2010.account.Call.creator(
                    new com.twilio.type.PhoneNumber(toPhoneNumber),
                    new com.twilio.type.PhoneNumber(twilioProperties.getPhoneNumber()),
                    new java.net.URI(twimlUrl)
                )
                .setStatusCallback(new java.net.URI(statusCallbackUrl))
                .setStatusCallbackEvent(java.util.Arrays.asList("initiated", "ringing", "answered", "completed"))
                .setStatusCallbackMethod(com.twilio.http.HttpMethod.POST)
                .create();

                String realCallSid = twilioCall.getSid();
                log.info("Twilio outbound call successfully created. Sid: {}", realCallSid);

                java.util.List<String> events = new java.util.ArrayList<>();
                events.add("Iniciando llamada saliente real a " + toPhoneNumber + " a través de Twilio...");
                events.add("Sid de llamada Twilio: " + realCallSid);
                
                LiveCall call = LiveCall.builder()
                        .id(realCallSid)
                        .phoneNumber(toPhoneNumber)
                        .userName(name)
                        .callAmount(amount)
                        .status("CONNECTED")
                        .direction("OUTBOUND")
                        .timestamp(LocalDateTime.now())
                        .callEvents(events)
                        .build();

                liveCalls.put(realCallSid, call);
                callRepository.save(call);
                broadcaster.broadcast(liveCalls.values());

                return IvrResponse.builder()
                        .message("Llamada saliente real iniciada con éxito. SID: " + realCallSid)
                        .nextAction("WAIT_FOR_INPUT")
                        .userId(userId)
                        .build();
            } catch (Exception e) {
                log.error("Failed to make real Twilio outbound call: {}", e.getMessage());
                return IvrResponse.builder()
                        .message("Error al iniciar llamada real: " + e.getMessage() + ". Reintentando simulación...")
                        .nextAction("FALLBACK_SIMULATION")
                        .build();
            }
        } else {
            // SIMULACIÓN DE FONDO CON HILOS
            final String simCallId = "SIM-OUT-" + UUID.randomUUID().toString().substring(0, 8);
            log.info("Initializing multi-step background call simulation. SID: {}", simCallId);

            java.util.List<String> events = new java.util.ArrayList<>();
            events.add("Iniciando llamada saliente simulada a " + toPhoneNumber + "...");
            events.add("Estableciendo handshake con el nodo telefónico público...");

            LiveCall call = LiveCall.builder()
                    .id(simCallId)
                    .phoneNumber(toPhoneNumber)
                    .userName(name)
                    .callAmount(amount)
                    .status("CONNECTED")
                    .direction("OUTBOUND")
                    .timestamp(LocalDateTime.now())
                    .callEvents(events)
                    .build();

            liveCalls.put(simCallId, call);
            callRepository.save(call);
            broadcaster.broadcast(liveCalls.values());

            scheduler.schedule(() -> {
                LiveCall c = liveCalls.get(simCallId);
                if (c != null) {
                    c.setStatus("WAITING_CONFIRMATION");
                    c.getCallEvents().add("El teléfono de destino está sonando...");
                    c.getCallEvents().add("Llamada contestada por " + finalName + ".");
                    c.getCallEvents().add("Bot IVR reproduciendo saludo interactivo: 'Hola " + finalName + ", tienes un pago pendiente de " + finalAmount + " euros...'");
                    c.getCallEvents().add("Esperando selección DTMF del usuario (1 o 2)...");
                    callRepository.save(c);
                    broadcaster.broadcast(liveCalls.values());
                }
            }, 3, java.util.concurrent.TimeUnit.SECONDS);

            scheduler.schedule(() -> {
                LiveCall c = liveCalls.get(simCallId);
                if (c != null && "WAITING_CONFIRMATION".equals(c.getStatus())) {
                    c.setStatus("PROCESSING_PAYMENT");
                    c.setSelectedOption("1");
                    c.getCallEvents().add("DTMF detectado: Usuario pulsó '1' (Confirmar Pago).");
                    c.getCallEvents().add("Llamando a Payment Service (/confirm) para procesar...");
                    callRepository.save(c);
                    broadcaster.broadcast(liveCalls.values());
                    
                    try {
                        paymentServiceClient.confirmPayment(finalUserId, getHeadersWithJwt());
                        c.setStatus("COMPLETED");
                        c.getCallEvents().add("Pago confirmado con éxito en la pasarela de pagos.");
                        c.getCallEvents().add("Notificación enviada a Notification Service (SMS enviado).");
                        c.getCallEvents().add("Reproduciendo despedida: 'Gracias. Su pago ha sido procesado correctamente...'");
                        c.getCallEvents().add("Llamada finalizada correctamente. Línea liberada.");
                        c.setDuration(12L);
                    } catch (Exception e) {
                        c.setStatus("FAILED");
                        c.getCallEvents().add("Error al confirmar el pago en la pasarela: " + e.getMessage());
                        c.getCallEvents().add("Llamada finalizada con error.");
                    }
                    callRepository.save(c);
                    broadcaster.broadcast(liveCalls.values());
                }
            }, 9, java.util.concurrent.TimeUnit.SECONDS);

            scheduler.schedule(() -> {
                liveCalls.remove(simCallId);
                broadcaster.broadcast(liveCalls.values());
                log.info("Simulated background call {} cleaned up.", simCallId);
            }, 14, java.util.concurrent.TimeUnit.SECONDS);

            return IvrResponse.builder()
                    .message("Llamada interactiva de simulación iniciada de fondo. Observa el dashboard en vivo.")
                    .nextAction("SIMULATION_RUNNING")
                    .userId(userId)
                    .build();
        }
    }

    public java.util.List<LiveCall> getCallHistory() {
        return callRepository.findAll();
    }

    public java.util.Collection<LiveCall> getLiveCalls() {
        return liveCalls.values();
    }
}

