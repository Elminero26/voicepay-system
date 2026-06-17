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
import com.twilio.twiml.voice.Pay;
import com.twilio.twiml.voice.Prompt;
import com.twilio.twiml.voice.Redirect;
import java.time.LocalDateTime;

import java.util.UUID;

import java.math.BigDecimal;
import java.util.Map;

import com.voicepay.ivr.client.UserServiceClient;
import com.voicepay.ivr.client.PaymentServiceClient;
import com.voicepay.ivr.client.UserFeignClient;
import com.voicepay.ivr.client.NotificationFeignClient;
import com.voicepay.ivr.dto.OtpGenerateRequest;
import com.voicepay.ivr.dto.OtpGenerateResponse;
import com.voicepay.ivr.dto.OtpValidateRequest;
import com.voicepay.ivr.dto.OtpValidateResponse;
import com.voicepay.ivr.dto.NotificationDto;
import org.springframework.beans.factory.annotation.Value;
import com.voicepay.ivr.nlp.NlpClient;
import com.voicepay.ivr.nlp.NlpResult;
import com.voicepay.ivr.nlp.exception.FallbackIntentException;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class IvrService {

    private final UserServiceClient userServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final UserFeignClient userFeignClient;
    private final NotificationFeignClient notificationFeignClient;
    private final LiveCallBroadcaster broadcaster;
    private final com.voicepay.ivr.repository.LiveCallRepository callRepository;
    private final com.voicepay.ivr.repository.IvrFlowConfigRepository flowConfigRepository;
    private final com.voicepay.ivr.config.TwilioProperties twilioProperties;
    private final Map<String, LiveCall> liveCalls = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Integer> localFailedSpeechAttempts = new java.util.concurrent.ConcurrentHashMap<>();


    private final com.voicepay.ivr.security.JwtUtil jwtUtil;
    private final NlpClient nlpClient;

    @Value("${app.otp.threshold:50.0}")
    private double otpThreshold;
    
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

                String promptTemplate = getVoicePromptFromConfig("3", "Bienvenido {name}. Usted tiene un pago pendiente de {amount} euros. Pulse 1 para pagar, o pulse 2 para hablar con un agente.");
                String dynamicMessage = promptTemplate.replace("{name}", name).replace("{amount}", amount.toString());

                return IvrResponse.builder()
                        .message(dynamicMessage)
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
                    .filter(c -> c.getStatus().equals("WAITING_CONFIRMATION") || c.getStatus().equals("PROCESSING_PAYMENT") || c.getStatus().equals("WAITING_OTP"))
                    .findFirst().orElse(null);
        }

        if (activeCall != null && "WAITING_OTP".equals(activeCall.getStatus())) {
            activeCall.getCallEvents().add("Usuario ingresó OTP para simulación: " + digits);
            activeCall.getCallEvents().add("Validando OTP contra el user-service...");
            try {
                OtpValidateRequest valReq = OtpValidateRequest.builder()
                        .identifier(activeCall.getPhoneNumber())
                        .code(digits)
                        .build();
                String authHeader = getHeadersWithJwt().getFirst("Authorization");
                OtpValidateResponse valRes = userFeignClient.validateOtp(valReq, authHeader);
                
                if (valRes.isValid()) {
                    activeCall.getCallEvents().add("OTP validado con éxito. Procesando pago...");
                    activeCall.setStatus("PROCESSING_PAYMENT");
                    callRepository.save(activeCall);
                    broadcaster.broadcast(liveCalls.values());
                    
                    paymentServiceClient.confirmPayment(userId, getHeadersWithJwt());
                    
                    activeCall.setStatus("COMPLETED");
                    activeCall.getCallEvents().add("Pago confirmado con éxito en pasarela.");
                    activeCall.getCallEvents().add("Llamando a Notification Service para enviar SMS/Push...");
                    activeCall.setDuration(java.time.Duration.between(activeCall.getTimestamp(), java.time.LocalDateTime.now()).getSeconds());
                    activeCall.setDirection(activeCall.getDirection() != null ? activeCall.getDirection() : "INBOUND");
                    callRepository.save(activeCall);
                    broadcaster.broadcast(liveCalls.values());
                    
                    final String finalCallId = activeCall.getId();
                    new java.util.Timer().schedule(new java.util.TimerTask() {
                        @Override
                        public void run() {
                            liveCalls.remove(finalCallId);
                            broadcaster.broadcast(liveCalls.values());
                        }
                    }, 5000);
                    
                    String promptTemplate = getVoicePromptFromConfig("5", "Gracias. Su pago ha sido procesado correctamente. Le hemos enviado un mensaje de confirmación a su móvil. ¡Adiós!");
                    String dynamicMessage = promptTemplate.replace("{name}", activeCall.getUserName()).replace("{amount}", String.valueOf(activeCall.getCallAmount()));
                    return IvrResponse.builder()
                            .message(dynamicMessage)
                            .nextAction("HANGUP")
                            .build();
                } else {
                    activeCall.setStatus("FAILED");
                    activeCall.getCallEvents().add("Fallo en la validación del OTP: " + valRes.getMessage());
                    callRepository.save(activeCall);
                    broadcaster.broadcast(liveCalls.values());
                    
                    final String finalCallId = activeCall.getId();
                    new java.util.Timer().schedule(new java.util.TimerTask() {
                        @Override
                        public void run() {
                            liveCalls.remove(finalCallId);
                            broadcaster.broadcast(liveCalls.values());
                        }
                    }, 5000);
                    
                    return IvrResponse.builder()
                            .message("El código OTP introducido es incorrecto o ha expirado. Operación cancelada. ¡Adiós!")
                            .nextAction("HANGUP")
                            .build();
                }
            } catch (Exception e) {
                log.error("Error validating OTP in simulation: {}", e.getMessage());
                activeCall.setStatus("FAILED");
                activeCall.getCallEvents().add("Error técnico al validar OTP: " + e.getMessage());
                callRepository.save(activeCall);
                broadcaster.broadcast(liveCalls.values());
                
                return IvrResponse.builder()
                        .message("Hubo un error técnico al validar el código OTP. Operación cancelada. ¡Adiós!")
                        .nextAction("HANGUP")
                        .build();
            }
        }

        if ("2".equals(digits)) {
            if (activeCall != null) {
                activeCall.setStatus("TRANSFERRED");
                activeCall.setSelectedOption("2");
                activeCall.getCallEvents().add("Usuario pulsó 2: Transfiriendo a un agente humano...");
                callRepository.save(activeCall);
                broadcaster.broadcast(liveCalls.values());
            }
            String promptTemplate = getVoicePromptFromConfig("6", "Un momento, por favor. Le estamos transfiriendo con el próximo agente disponible.");
            return IvrResponse.builder()
                    .message(promptTemplate)
                    .nextAction("TRANSFER")
                    .build();
        } else if ("1".equals(digits)) {
            if (activeCall != null) {
                if (activeCall.getCallAmount() > otpThreshold) {
                    activeCall.setStatus("WAITING_OTP");
                    activeCall.setSelectedOption(digits);
                    activeCall.getCallEvents().add("Usuario pulsó " + digits + ": El monto de " + activeCall.getCallAmount() + " EUR supera el umbral de " + otpThreshold + " EUR. Requiere validación de OTP.");
                    activeCall.getCallEvents().add("Llamando a User Service para generar OTP...");
                    
                    try {
                        OtpGenerateRequest req = OtpGenerateRequest.builder()
                                .identifier(activeCall.getPhoneNumber())
                                .length(6)
                                .ttlMinutes(3)
                                .build();
                        String authHeader = getHeadersWithJwt().getFirst("Authorization");
                        OtpGenerateResponse otpRes = userFeignClient.generateOtp(req, authHeader);
                        activeCall.getCallEvents().add("Código OTP generado con éxito.");
                        
                        NotificationDto notif = NotificationDto.builder()
                                .recipient(activeCall.getPhoneNumber())
                                .message("Su codigo de seguridad de VoicePay para confirmar su pago de " + activeCall.getCallAmount() + " EUR es: " + otpRes.getCode() + ". No lo comparta con nadie.")
                                .type("SMS")
                                .build();
                        notificationFeignClient.sendNotification(notif, authHeader);
                        activeCall.getCallEvents().add("Notificación de OTP enviada al número " + activeCall.getPhoneNumber());
                    } catch (Exception e) {
                        log.error("Fallo al generar/notificar OTP en simulación: {}", e.getMessage());
                        activeCall.getCallEvents().add("Error al generar/notificar OTP: " + e.getMessage());
                    }
                    
                    callRepository.save(activeCall);
                    broadcaster.broadcast(liveCalls.values());
                    
                    return IvrResponse.builder()
                            .message("El monto del pago supera el límite de seguridad. Le hemos enviado un código de verificación SMS a su móvil. Por favor, introduzca el código OTP.")
                            .nextAction("WAIT_FOR_OTP")
                            .userId(userId)
                            .build();
                }

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

            String promptTemplate = getVoicePromptFromConfig("5", "Gracias. Su pago ha sido procesado correctamente. Le hemos enviado un mensaje de confirmación a su móvil. ¡Adiós!");
            String dynamicMessage = promptTemplate.replace("{name}", activeCall != null ? activeCall.getUserName() : "Cliente").replace("{amount}", activeCall != null ? String.valueOf(activeCall.getCallAmount()) : "0");

            return IvrResponse.builder()
                    .message(dynamicMessage)
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

    public String handleTwilioAmdCallback(String from, String callSid, String answeredBy, String baseUrl) {
        log.info("Received Twilio AMD callback: from={}, callSid={}, answeredBy={}", from, callSid, answeredBy);

        LiveCall call = liveCalls.get(callSid);
        if (call == null) {
            call = callRepository.findById(callSid).orElse(null);
        }

        boolean isMachine = answeredBy != null && (
            answeredBy.toLowerCase().startsWith("machine") ||
            answeredBy.toLowerCase().contains("voicemail") ||
            answeredBy.toLowerCase().contains("message")
        );

        if (isMachine) {
            log.info("AMD detected machine for call {}. Updating campaign member to retry state.", callSid);
            if (call != null) {
                call.getCallEvents().add("Contestador automático detectado (" + answeredBy + "). Colgando llamada.");
                call.setStatus("MACHINE_DETECTED");
                callRepository.save(call);

                // Update CampaignMember status to PENDING (retry state)
                if (call.getCampaignMemberId() != null) {
                    updateCampaignMemberCallStatus(call.getCampaignMemberId(), "PENDING_RETRY");
                }

                broadcaster.broadcast(liveCalls.values());
            }

            // Return TwiML to say a short message and hang up
            return new VoiceResponse.Builder()
                    .say(new Say.Builder("Buzón de voz detectado. Intentaremos contactarle más tarde. Gracias.")
                            .language(Say.Language.ES_ES).build())
                    .hangup(new Hangup.Builder().build())
                    .build().toXml();
        } else {
            // "Si detecta una voz humana, debe hacer un reenvío inmediato (forward) al flujo raíz del IVR de pagos de manera transparente."
            log.info("AMD detected human or other for call {}. Redirecting to IVR root flow.", callSid);
            if (call != null) {
                call.getCallEvents().add("Voz humana detectada. Conectando al IVR de pagos...");
                callRepository.save(call);
                broadcaster.broadcast(liveCalls.values());
            }

            String domain = baseUrl;
            if (domain == null || domain.isEmpty()) {
                domain = twilioProperties.getWebhookUrl();
            }
            if (domain == null || domain.isEmpty()) {
                domain = "http://localhost:8082";
            }
            if (domain.endsWith("/")) {
                domain = domain.substring(0, domain.length() - 1);
            }

            String redirectUrl = "";
            try {
                redirectUrl = domain + "/ivr/twilio-call?From=" + java.net.URLEncoder.encode(from, "UTF-8")
                        + "&CallSid=" + callSid;
            } catch (Exception e) {
                log.error("Encoding error: {}", e.getMessage());
                redirectUrl = domain + "/ivr/twilio-call?From=" + from + "&CallSid=" + callSid;
            }

            return new VoiceResponse.Builder()
                    .redirect(new Redirect.Builder(redirectUrl).build())
                    .build().toXml();
        }
    }

    public String handleTwilioCall(String from) {
        return handleTwilioCall(from, UUID.randomUUID().toString());
    }

    public String handleTwilioCall(String from, String callSid) {
        return handleTwilioCall(from, callSid, null);
    }

    public String handleTwilioCall(String from, String callSid, String baseUrl) {
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

            // Generamos TwiML dinámico
            String welcomeTemplate = getVoicePromptFromConfig("1", "Hola {name}. Bienvenido a Voice Pay.");
            String welcomePrompt = welcomeTemplate.replace("{name}", name).replace("{amount}", amountStr);

            String queryTemplate = getVoicePromptFromConfig("3", "Usted tiene un pago pendiente de {amount} euros.");
            String queryPrompt = queryTemplate.replace("{name}", name).replace("{amount}", amountStr);

            String gatherTemplate = getVoicePromptFromConfig("4", "Para confirmar el pago, pulse 1. Para hablar con un agente, pulse 2. Para cancelar, cuelgue.");
            String gatherPrompt = gatherTemplate.replace("{name}", name).replace("{amount}", amountStr);

            String domain = baseUrl;
            if (domain == null || domain.isEmpty()) {
                domain = twilioProperties.getWebhookUrl();
            }
            if (domain == null || domain.isEmpty()) {
                domain = "http://localhost:8082";
            }
            if (domain.endsWith("/")) {
                domain = domain.substring(0, domain.length() - 1);
            }

            return new VoiceResponse.Builder()
                    .say(new Say.Builder(welcomePrompt + " " + queryPrompt)
                            .language(Say.Language.ES_ES).build())
                    .gather(new Gather.Builder()
                            .inputs(java.util.Arrays.asList(Gather.Input.SPEECH, Gather.Input.DTMF))
                            .language(Gather.Language.ES_ES)
                            .speechTimeout("auto")
                            .numDigits(1)
                            .action("/ivr/twilio-webhook?userId=" + userId + "&callId=" + callSid)
                            .partialResultCallback(domain + "/ivr/twilio-webhook?userId=" + userId + "&callId=" + callSid)
                            .say(new Say.Builder(gatherPrompt)
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

    public String handleTwilioWebhook(Long userId, String callId, String digits, String speechResult, String baseUrl) {
        return handleTwilioWebhook(userId, callId, digits, speechResult, null, baseUrl);
    }

    public String handleTwilioWebhook(Long userId, String callId, String digits, String speechResult, String unstableSpeechResult, String baseUrl) {
        log.info("Received Twilio webhook: userId={}, digits={}, speechResult={}, unstableSpeechResult={}, callId={}", 
                 userId, digits, speechResult, unstableSpeechResult, callId);
        
        if (unstableSpeechResult != null && !unstableSpeechResult.isEmpty()) {
            log.info("Partial transcription received for callId {}: {}", callId, unstableSpeechResult);
            broadcaster.broadcastTranscription(callId, "user", unstableSpeechResult);
            return "<Response/>";
        }

        if (speechResult != null && !speechResult.isEmpty()) {
            log.info("Final transcription received for callId {}: {}", callId, speechResult);
            broadcaster.broadcastTranscription(callId, "user", speechResult);
        }

        LiveCall call = liveCalls.get(callId);
        if (call != null) {
            if (speechResult != null && !speechResult.isEmpty()) {
                call.getCallEvents().add("Entrada por voz recibida: \"" + speechResult + "\"");
            }
            if (digits != null && !digits.isEmpty()) {
                call.getCallEvents().add("Dígito DTMF recibido: \"" + digits + "\"");
            }
            if ((digits == null || digits.isEmpty()) && (speechResult == null || speechResult.isEmpty())) {
                call.getCallEvents().add("No se recibió ninguna entrada (tiempo de espera agotado).");
            }
        }

        String resolvedOption = (digits != null) ? digits.trim() : "";
        boolean isSpeechFailure = false;

        if (resolvedOption.isEmpty()) {
            if (speechResult == null || speechResult.trim().isEmpty()) {
                isSpeechFailure = true;
            } else {
                try {
                    NlpResult nlpResult = nlpClient.analyzeText(speechResult);
                    String intent = nlpResult.getIntent();
                    if ("PAY_DEBT".equals(intent)) {
                        resolvedOption = "1";
                        if (call != null) {
                            call.getCallEvents().add("Intención identificada (Pagar) con confianza " + nlpResult.getConfidence() + ": \"" + speechResult + "\"");
                        }
                    } else if ("TALK_TO_AGENT".equals(intent)) {
                        resolvedOption = "2";
                        if (call != null) {
                            call.getCallEvents().add("Intención identificada (Hablar con agente) con confianza " + nlpResult.getConfidence() + ": \"" + speechResult + "\"");
                        }
                    } else if ("CANCEL".equals(intent)) {
                        resolvedOption = "3";
                        if (call != null) {
                            call.getCallEvents().add("Intención identificada (Cancelar) con confianza " + nlpResult.getConfidence() + ": \"" + speechResult + "\"");
                        }
                    }
                } catch (FallbackIntentException e) {
                    log.warn("NLP processing fallback for text \"{}\": {}", speechResult, e.getMessage());
                    if (call != null) {
                        call.getCallEvents().add("Error NLP: Intención no reconocida (FALLBACK) para el texto: \"" + speechResult + "\"");
                    }
                    isSpeechFailure = true;
                }
            }
        }

        if (isSpeechFailure) {
            int failedAttempts = 0;
            if (call != null) {
                failedAttempts = call.getFailedSpeechAttempts() + 1;
                call.setFailedSpeechAttempts(failedAttempts);
                call.getCallEvents().add("Intento fallido de voz consecutivo #" + failedAttempts);
                callRepository.save(call);
                broadcaster.broadcast(liveCalls.values());
            } else {
                failedAttempts = localFailedSpeechAttempts.getOrDefault(callId, 0) + 1;
                localFailedSpeechAttempts.put(callId, failedAttempts);
            }

            String domain = baseUrl;
            if (domain == null || domain.isEmpty()) {
                domain = twilioProperties.getWebhookUrl();
            }
            if (domain == null || domain.isEmpty()) {
                domain = "http://localhost:8082";
            }
            if (domain.endsWith("/")) {
                domain = domain.substring(0, domain.length() - 1);
            }

            if (failedAttempts == 1) {
                String retryPrompt = "No le hemos entendido. Por favor, vuelva a intentarlo. Para confirmar el pago, pulse 1 o diga pagar. Para hablar con un agente, pulse 2 o diga agente.";
                return new VoiceResponse.Builder()
                        .gather(new Gather.Builder()
                                .inputs(java.util.Arrays.asList(Gather.Input.SPEECH, Gather.Input.DTMF))
                                .language(Gather.Language.ES_ES)
                                .speechTimeout("auto")
                                .numDigits(1)
                                .action("/ivr/twilio-webhook?userId=" + userId + "&callId=" + callId)
                                .partialResultCallback(domain + "/ivr/twilio-webhook?userId=" + userId + "&callId=" + callId)
                                .say(new Say.Builder(retryPrompt)
                                        .language(Say.Language.ES_ES).build())
                                .build())
                        .build().toXml();
            } else if (failedAttempts == 2) {
                String dtmfPrompt = "No hemos podido entender su voz. Por favor, use el teclado numérico de su teléfono. Pulse 1 para confirmar el pago, o pulse 2 para hablar con un agente.";
                return new VoiceResponse.Builder()
                        .gather(new Gather.Builder()
                                .inputs(java.util.Arrays.asList(Gather.Input.DTMF))
                                .numDigits(1)
                                .action("/ivr/twilio-webhook?userId=" + userId + "&callId=" + callId)
                                .say(new Say.Builder(dtmfPrompt)
                                        .language(Say.Language.ES_ES).build())
                                .build())
                        .build().toXml();
            } else {
                if (call != null) {
                    call.setStatus("FAILED");
                    call.getCallEvents().add("Llamada terminada tras " + failedAttempts + " intentos de voz fallidos.");
                    callRepository.save(call);
                    broadcaster.broadcast(liveCalls.values());
                }
                localFailedSpeechAttempts.remove(callId);

                return new VoiceResponse.Builder()
                        .say(new Say.Builder("No hemos recibido una respuesta válida. Operación cancelada. ¡Adiós!")
                                .language(Say.Language.ES_ES).build())
                        .hangup(new Hangup.Builder().build())
                        .build().toXml();
            }
        }

        if (call != null) {
            callRepository.save(call);
            broadcaster.broadcast(liveCalls.values());
        }

        if ("1".equals(resolvedOption) || "2".equals(resolvedOption) || "3".equals(resolvedOption)) {
            log.info("User selected option {} (derived from digits/speech)", resolvedOption);
            
            if (call != null) {
                call.setFailedSpeechAttempts(0);
                callRepository.save(call);
                broadcaster.broadcast(liveCalls.values());
            }
            localFailedSpeechAttempts.remove(callId);

            if ("1".equals(resolvedOption)) {
                double amount = call != null ? call.getCallAmount() : 25.00;
                if (amount > otpThreshold) {
                    if (call != null) {
                        call.setStatus("WAITING_OTP");
                        call.setSelectedOption(resolvedOption);
                        call.getCallEvents().add("El monto de " + amount + " EUR supera el umbral de seguridad de " + otpThreshold + " EUR. Requiere validación de OTP.");
                        call.getCallEvents().add("Llamando a User Service para generar OTP...");
                        
                        try {
                            OtpGenerateRequest req = OtpGenerateRequest.builder()
                                    .identifier(call.getPhoneNumber())
                                    .length(6)
                                    .ttlMinutes(3)
                                    .build();
                            String authHeader = getHeadersWithJwt().getFirst("Authorization");
                            OtpGenerateResponse otpRes = userFeignClient.generateOtp(req, authHeader);
                            call.getCallEvents().add("Código OTP generado con éxito.");
                            
                            NotificationDto notif = NotificationDto.builder()
                                    .recipient(call.getPhoneNumber())
                                    .message("Su codigo de seguridad de VoicePay para confirmar su pago de " + amount + " EUR es: " + otpRes.getCode() + ". No lo comparta con nadie.")
                                    .type("SMS")
                                    .build();
                            notificationFeignClient.sendNotification(notif, authHeader);
                            call.getCallEvents().add("Notificación de OTP enviada al número " + call.getPhoneNumber());
                        } catch (Exception e) {
                            log.error("Fallo al generar/notificar OTP en Twilio webhook: {}", e.getMessage());
                            call.getCallEvents().add("Error al generar/notificar OTP: " + e.getMessage());
                        }
                        
                        callRepository.save(call);
                        broadcaster.broadcast(liveCalls.values());
                    }
                    
                    String amountStr = String.format(java.util.Locale.US, "%.2f", amount);
                    String otpPrompt = "Su pago de " + amountStr + " euros supera el límite de seguridad. Le hemos enviado un código de verificación por SMS. Por favor, introduzca el código de seis dígitos ahora.";
                    
                    String gatherUrl = baseUrl;
                    if (gatherUrl == null || gatherUrl.isEmpty()) {
                        gatherUrl = twilioProperties.getWebhookUrl();
                    }
                    if (gatherUrl == null || gatherUrl.isEmpty()) {
                        gatherUrl = "http://localhost:8082";
                    }
                    if (gatherUrl.endsWith("/")) {
                        gatherUrl = gatherUrl.substring(0, gatherUrl.length() - 1);
                    }
                    
                    return new VoiceResponse.Builder()
                            .gather(new Gather.Builder()
                                    .inputs(java.util.Arrays.asList(Gather.Input.DTMF))
                                    .numDigits(6)
                                    .action(gatherUrl + "/ivr/twilio-webhook-otp?userId=" + userId + "&callId=" + callId)
                                    .say(new Say.Builder(otpPrompt)
                                            .language(Say.Language.ES_ES).build())
                                    .build())
                            .build().toXml();
                }

                String amountStr = "25.00";
                if (call != null) {
                    call.setStatus("PROCESSING_PAYMENT");
                    call.setSelectedOption(resolvedOption);
                    call.getCallEvents().add("Iniciando cobro seguro vía Twilio Pay...");
                    amountStr = String.format(java.util.Locale.US, "%.2f", call.getCallAmount());
                    callRepository.save(call);
                    broadcaster.broadcast(liveCalls.values());
                }

                String connectorName = twilioProperties.getPaymentConnector();
                if (connectorName == null || connectorName.isEmpty()) {
                    connectorName = "stripe_connector";
                }
                String actionUrl = baseUrl + "/ivr/twilio-pay-action?userId=" + userId;

                Prompt cardPrompt = new Prompt.Builder()
                        .for_(Prompt.For.PAYMENT_CARD_NUMBER)
                        .say(new Say.Builder("Por favor, introduzca los dieciséis dígitos de su tarjeta de crédito.")
                                .language(Say.Language.ES_ES).build())
                        .build();

                Prompt expiryPrompt = new Prompt.Builder()
                        .for_(Prompt.For.EXPIRATION_DATE)
                        .say(new Say.Builder("Introduzca la fecha de caducidad con dos dígitos para el mes y dos para el año. Por ejemplo, doce veintiséis.")
                                .language(Say.Language.ES_ES).build())
                        .build();

                Prompt cvcPrompt = new Prompt.Builder()
                        .for_(Prompt.For.SECURITY_CODE)
                        .say(new Say.Builder("Por favor, introduzca el código de seguridad de tres dígitos al dorso de su tarjeta.")
                                .language(Say.Language.ES_ES).build())
                        .build();

                Pay pay = new Pay.Builder()
                        .paymentConnector(connectorName)
                        .chargeAmount(amountStr)
                        .currency("eur")
                        .action(actionUrl)
                        .prompt(cardPrompt)
                        .prompt(expiryPrompt)
                        .prompt(cvcPrompt)
                        .build();

                return new VoiceResponse.Builder()
                        .pay(pay)
                        .build().toXml();
            } else if ("2".equals(resolvedOption)) {
                IvrResponse response = processUserOptionWithCallId(userId, resolvedOption, callId);
                
                return new VoiceResponse.Builder()
                        .say(new Say.Builder(response.getMessage())
                                .language(Say.Language.ES_ES).build())
                        .hangup(new Hangup.Builder().build())
                        .build().toXml();
            } else {
                if (call != null) {
                    call.setStatus("FAILED");
                    call.setSelectedOption("3");
                    call.getCallEvents().add("Llamada cancelada por el usuario.");
                    callRepository.save(call);
                    broadcaster.broadcast(liveCalls.values());
                }
                return new VoiceResponse.Builder()
                        .say(new Say.Builder("Operación cancelada. ¡Adiós!")
                                .language(Say.Language.ES_ES).build())
                        .hangup(new Hangup.Builder().build())
                        .build().toXml();
            }
        }

        if (call != null) {
            call.setStatus("FAILED");
            if (speechResult != null && !speechResult.isEmpty()) {
                call.getCallEvents().add("Usuario seleccionó una opción de voz inválida: " + speechResult);
            } else {
                call.getCallEvents().add("Usuario seleccionó una opción inválida DTMF: " + digits);
            }
            callRepository.save(call);
            broadcaster.broadcast(liveCalls.values());
        }
        localFailedSpeechAttempts.remove(callId);

        return new VoiceResponse.Builder()
                .say(new Say.Builder("Opción inválida. Operación cancelada. ¡Adiós!")
                        .language(Say.Language.ES_ES).build())
                .hangup(new Hangup.Builder().build())
                .build().toXml();
    }

    public String handleTwilioWebhookOtp(Long userId, String callId, String digits, String baseUrl) {
        log.info("Received Twilio OTP webhook: userId={}, callId={}, digits={}", userId, callId, digits);
        
        LiveCall activeCall = liveCalls.get(callId);
        if (activeCall == null) {
            activeCall = callRepository.findById(callId).orElse(null);
        }
        
        if (activeCall != null) {
            activeCall.getCallEvents().add("Código OTP ingresado vía DTMF: " + digits);
        }
        
        try {
            String identifier = activeCall != null ? activeCall.getPhoneNumber() : String.valueOf(userId);
            OtpValidateRequest valReq = OtpValidateRequest.builder()
                    .identifier(identifier)
                    .code(digits)
                    .build();
            String authHeader = getHeadersWithJwt().getFirst("Authorization");
            OtpValidateResponse valRes = userFeignClient.validateOtp(valReq, authHeader);
            
            if (valRes.isValid()) {
                log.info("OTP validated successfully for user: {}", userId);
                if (activeCall != null) {
                    activeCall.getCallEvents().add("OTP validado con éxito. Iniciando cobro seguro vía Twilio Pay...");
                    activeCall.setStatus("PROCESSING_PAYMENT");
                    callRepository.save(activeCall);
                    broadcaster.broadcast(liveCalls.values());
                }
                
                String amountStr = "25.00";
                if (activeCall != null) {
                    amountStr = String.format(java.util.Locale.US, "%.2f", activeCall.getCallAmount());
                }
                
                String connectorName = twilioProperties.getPaymentConnector();
                if (connectorName == null || connectorName.isEmpty()) {
                    connectorName = "stripe_connector";
                }
                String actionUrl = baseUrl + "/ivr/twilio-pay-action?userId=" + userId;
                
                Prompt cardPrompt = new Prompt.Builder()
                        .for_(Prompt.For.PAYMENT_CARD_NUMBER)
                        .say(new Say.Builder("Por favor, introduzca los dieciséis dígitos de su tarjeta de crédito.")
                                .language(Say.Language.ES_ES).build())
                        .build();

                Prompt expiryPrompt = new Prompt.Builder()
                        .for_(Prompt.For.EXPIRATION_DATE)
                        .say(new Say.Builder("Introduzca la fecha de caducidad con dos dígitos para el mes y dos para el año. Por ejemplo, doce veintiséis.")
                                .language(Say.Language.ES_ES).build())
                        .build();

                Prompt cvcPrompt = new Prompt.Builder()
                        .for_(Prompt.For.SECURITY_CODE)
                        .say(new Say.Builder("Por favor, introduzca el código de seguridad de tres dígitos al dorso de su tarjeta.")
                                .language(Say.Language.ES_ES).build())
                        .build();

                Pay pay = new Pay.Builder()
                        .paymentConnector(connectorName)
                        .chargeAmount(amountStr)
                        .currency("eur")
                        .action(actionUrl)
                        .prompt(cardPrompt)
                        .prompt(expiryPrompt)
                        .prompt(cvcPrompt)
                        .build();

                return new VoiceResponse.Builder()
                        .pay(pay)
                        .build().toXml();
            } else {
                log.warn("OTP validation failed: {}", valRes.getMessage());
                if (activeCall != null) {
                    activeCall.setStatus("FAILED");
                    activeCall.getCallEvents().add("Fallo en validación de OTP: " + valRes.getMessage());
                    callRepository.save(activeCall);
                    broadcaster.broadcast(liveCalls.values());
                    
                    final String finalCallId = activeCall.getId();
                    new java.util.Timer().schedule(new java.util.TimerTask() {
                        @Override
                        public void run() {
                            liveCalls.remove(finalCallId);
                            broadcaster.broadcast(liveCalls.values());
                        }
                    }, 5000);
                }
                
                return new VoiceResponse.Builder()
                        .say(new Say.Builder("El código OTP es incorrecto. La operación ha sido cancelada. ¡Adiós!")
                                .language(Say.Language.ES_ES).build())
                        .hangup(new Hangup.Builder().build())
                        .build().toXml();
            }
        } catch (Exception e) {
            log.error("Error validating OTP in Twilio webhook: {}", e.getMessage());
            if (activeCall != null) {
                activeCall.setStatus("FAILED");
                activeCall.getCallEvents().add("Error técnico en validación de OTP: " + e.getMessage());
                callRepository.save(activeCall);
                broadcaster.broadcast(liveCalls.values());
            }
            return new VoiceResponse.Builder()
                    .say(new Say.Builder("Hubo un error técnico al validar su código de seguridad. La operación ha sido cancelada. ¡Adiós!")
                            .language(Say.Language.ES_ES).build())
                    .hangup(new Hangup.Builder().build())
                    .build().toXml();
        }
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
                if (!"COMPLETED".equals(call.getStatus()) && !"TRANSFERRED".equals(call.getStatus()) && !"FAILED".equals(call.getStatus()) && !"MACHINE_DETECTED".equals(call.getStatus())) {
                    call.setStatus("FAILED");
                    call.getCallEvents().add("La llamada finalizó sin confirmación del usuario.");
                }
            } else if ("failed".equalsIgnoreCase(callStatus)) {
                if (!"MACHINE_DETECTED".equals(call.getStatus())) {
                    call.setStatus("FAILED");
                }
                call.getCallEvents().add("Error de conexión de Twilio.");
            } else if ("busy".equalsIgnoreCase(callStatus)) {
                if (!"MACHINE_DETECTED".equals(call.getStatus())) {
                    call.setStatus("FAILED");
                }
                call.getCallEvents().add("Línea ocupada.");
            } else if ("no-answer".equalsIgnoreCase(callStatus)) {
                if (!"MACHINE_DETECTED".equals(call.getStatus())) {
                    call.setStatus("FAILED");
                }
                call.getCallEvents().add("Sin respuesta del usuario.");
            } else if ("canceled".equalsIgnoreCase(callStatus)) {
                if (!"MACHINE_DETECTED".equals(call.getStatus())) {
                    call.setStatus("FAILED");
                }
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
                
                // Update CampaignMember status
                if (call.getCampaignMemberId() != null && !"MACHINE_DETECTED".equals(call.getStatus())) {
                    String finalMemberStatus = "NO_ANSWER";
                    if ("completed".equalsIgnoreCase(callStatus) && "COMPLETED".equals(call.getStatus())) {
                        finalMemberStatus = "COMPLETED";
                    } else if ("busy".equalsIgnoreCase(callStatus)) {
                        finalMemberStatus = "BUSY";
                    }
                    updateCampaignMemberCallStatus(call.getCampaignMemberId(), finalMemberStatus);
                }

                final String sid = callSid;
                new java.util.Timer().schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        liveCalls.remove(sid);
                        localFailedSpeechAttempts.remove(sid);
                        broadcaster.broadcast(liveCalls.values());
                    }
                }, 5000);

            }
        }
    }

    public IvrResponse triggerOutboundCall(String toPhoneNumber, boolean forceMock) {
        return triggerOutboundCall(toPhoneNumber, null, forceMock);
    }

    public IvrResponse triggerOutboundCall(String toPhoneNumber, Long campaignMemberId, boolean forceMock) {
        log.info("Triggering outbound call to: {}, campaignMemberId={}, forceMock={}", toPhoneNumber, campaignMemberId, forceMock);

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
            if (pendingPayment != null && paymentDataNotEmpty(pendingPayment)) {
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
                
                String twimlUrl = domain + "/ivr/twilio-amd-callback?From=" + java.net.URLEncoder.encode(toPhoneNumber, "UTF-8") 
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
                .setMachineDetection("Enable")
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
                        .campaignMemberId(campaignMemberId)
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
                updateCampaignMemberCallStatus(campaignMemberId, "FAILED");
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
                    .campaignMemberId(campaignMemberId)
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

            if (amount > otpThreshold) {
                final java.util.concurrent.atomic.AtomicReference<String> generatedOtpRef = new java.util.concurrent.atomic.AtomicReference<>("123456");

                scheduler.schedule(() -> {
                    LiveCall c = liveCalls.get(simCallId);
                    if (c != null && "WAITING_CONFIRMATION".equals(c.getStatus())) {
                        c.setStatus("WAITING_OTP");
                        c.setSelectedOption("1");
                        c.getCallEvents().add("DTMF detectado: Usuario pulsó '1' (Confirmar Pago).");
                        c.getCallEvents().add("El monto de " + finalAmount + " EUR supera el umbral de seguridad de " + otpThreshold + " EUR. Requiere validación de OTP.");
                        c.getCallEvents().add("Llamando a User Service para generar OTP...");
                        
                        try {
                            OtpGenerateRequest req = OtpGenerateRequest.builder()
                                    .identifier(c.getPhoneNumber())
                                    .length(6)
                                    .ttlMinutes(3)
                                    .build();
                            String authHeader = getHeadersWithJwt().getFirst("Authorization");
                            OtpGenerateResponse otpRes = userFeignClient.generateOtp(req, authHeader);
                            generatedOtpRef.set(otpRes.getCode());
                            c.getCallEvents().add("Código OTP generado con éxito.");
                            
                            NotificationDto notif = NotificationDto.builder()
                                    .recipient(c.getPhoneNumber())
                                    .message("Su codigo de seguridad de VoicePay para confirmar su pago de " + finalAmount + " EUR es: " + otpRes.getCode() + ". No lo comparta con nadie.")
                                    .type("SMS")
                                    .build();
                            notificationFeignClient.sendNotification(notif, authHeader);
                            c.getCallEvents().add("Notificación de OTP enviada al número " + c.getPhoneNumber());
                        } catch (Exception e) {
                            log.error("Fallo al generar/notificar OTP en simulación: {}", e.getMessage());
                            c.getCallEvents().add("Error al generar/notificar OTP en simulación: " + e.getMessage());
                        }
                        
                        c.getCallEvents().add("Esperando a que el usuario introduzca el código OTP de 6 dígitos...");
                        callRepository.save(c);
                        broadcaster.broadcast(liveCalls.values());
                    }
                }, 9, java.util.concurrent.TimeUnit.SECONDS);

                scheduler.schedule(() -> {
                    LiveCall c = liveCalls.get(simCallId);
                    if (c != null && "WAITING_OTP".equals(c.getStatus())) {
                        c.getCallEvents().add("Usuario digitó código OTP recibido.");
                        c.getCallEvents().add("Llamando a User Service para validar OTP...");
                        
                        try {
                            String authHeader = getHeadersWithJwt().getFirst("Authorization");
                            OtpValidateRequest valReq = OtpValidateRequest.builder()
                                    .identifier(c.getPhoneNumber())
                                    .code(generatedOtpRef.get())
                                    .build();
                            OtpValidateResponse valRes = userFeignClient.validateOtp(valReq, authHeader);
                            
                            if (valRes.isValid()) {
                                c.getCallEvents().add("OTP validado con éxito. Procesando pago...");
                                c.setStatus("PROCESSING_PAYMENT");
                                callRepository.save(c);
                                broadcaster.broadcast(liveCalls.values());
                                
                                paymentServiceClient.confirmPayment(finalUserId, getHeadersWithJwt());
                                c.setStatus("COMPLETED");
                                c.getCallEvents().add("Pago confirmado con éxito en la pasarela de pagos.");
                                c.getCallEvents().add("Notificación enviada a Notification Service (SMS enviado).");
                                c.getCallEvents().add("Reproduciendo despedida: 'Gracias. Su pago ha sido procesado correctamente...'");
                                c.getCallEvents().add("Llamada finalizada correctamente. Línea liberada.");
                                c.setDuration(12L);
                                updateCampaignMemberCallStatus(campaignMemberId, "COMPLETED");
                            } else {
                                c.setStatus("FAILED");
                                c.getCallEvents().add("Fallo en la validación del OTP: " + valRes.getMessage());
                                c.getCallEvents().add("Llamada finalizada con error.");
                                updateCampaignMemberCallStatus(campaignMemberId, "FAILED");
                            }
                        } catch (Exception e) {
                            c.setStatus("FAILED");
                            c.getCallEvents().add("Error al validar OTP o confirmar el pago: " + e.getMessage());
                            c.getCallEvents().add("Llamada finalizada con error.");
                            updateCampaignMemberCallStatus(campaignMemberId, "FAILED");
                        }
                        callRepository.save(c);
                        broadcaster.broadcast(liveCalls.values());
                    }
                }, 14, java.util.concurrent.TimeUnit.SECONDS);

                scheduler.schedule(() -> {
                    liveCalls.remove(simCallId);
                    broadcaster.broadcast(liveCalls.values());
                    log.info("Simulated background call {} cleaned up.", simCallId);
                }, 19, java.util.concurrent.TimeUnit.SECONDS);
            } else {
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
                            updateCampaignMemberCallStatus(campaignMemberId, "COMPLETED");
                        } catch (Exception e) {
                            c.setStatus("FAILED");
                            c.getCallEvents().add("Error al confirmar el pago en la pasarela: " + e.getMessage());
                            c.getCallEvents().add("Llamada finalizada con error.");
                            updateCampaignMemberCallStatus(campaignMemberId, "FAILED");
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
            }

            return IvrResponse.builder()
                    .message("Llamada interactiva de simulación iniciada de fondo. Observa el dashboard en vivo.")
                    .nextAction("SIMULATION_RUNNING")
                    .userId(userId)
                    .build();
        }
    }

    private boolean paymentDataNotEmpty(Map<String, Object> paymentData) {
        return paymentData.get("amount") != null && !paymentData.get("amount").toString().trim().isEmpty();
    }

    private static final String DEFAULT_FLOW_JSON = "{\"nodes\":[" +
            "{\"id\":\"1\",\"type\":\"ivrNode\",\"position\":{\"x\":250,\"y\":50},\"data\":{\"label\":\"Incoming Call\",\"description\":\"User dials the IVR system\",\"status\":\"pending\",\"icon\":\"PhoneCall\",\"voicePrompt\":\"Bienvenido al sistema de pagos automáticos VoicePay. Por favor, espere mientras le identificamos.\",\"apiEndpoint\":\"https://api.voicepay.com/v1/ivr/welcome\"}}," +
            "{\"id\":\"2\",\"type\":\"ivrNode\",\"position\":{\"x\":250,\"y\":180},\"data\":{\"label\":\"Authentication\",\"description\":\"Identifying user by phone\",\"status\":\"pending\",\"icon\":\"ShieldCheck\",\"voicePrompt\":\"Para garantizar su seguridad, estamos verificando el número de teléfono desde el que nos llama.\",\"apiEndpoint\":\"https://api.voicepay.com/v1/users/verify\"}}," +
            "{\"id\":\"3\",\"type\":\"ivrNode\",\"position\":{\"x\":250,\"y\":310},\"data\":{\"label\":\"Payment Inquiry\",\"description\":\"Checking pending amount\",\"status\":\"pending\",\"icon\":\"CreditCard\",\"voicePrompt\":\"Hemos detectado una factura pendiente de {amount} euros. Pulse uno para proceder con el pago seguro con tarjeta, o pulse dos si prefiere ser atendido por un agente.\",\"apiEndpoint\":\"https://api.voicepay.com/v1/payments/inquiry\"}}," +
            "{\"id\":\"4\",\"type\":\"ivrNode\",\"position\":{\"x\":250,\"y\":440},\"data\":{\"label\":\"User Selection\",\"description\":\"Waiting for DTMF (1 or 2)\",\"status\":\"pending\",\"icon\":\"User\",\"voicePrompt\":\"Esperando su selección. Marque uno para pagar, o dos para soporte.\",\"apiEndpoint\":\"https://api.voicepay.com/v1/ivr/selection\"}}," +
            "{\"id\":\"5\",\"type\":\"ivrNode\",\"position\":{\"x\":50,\"y\":580},\"data\":{\"label\":\"Payment Status\",\"description\":\"Final transaction result\",\"status\":\"pending\",\"icon\":\"CheckCircle2\",\"voicePrompt\":\"Su pago de {amount} euros ha sido procesado y aprobado correctamente. Muchas gracias por utilizar VoicePay. Hasta pronto.\",\"apiEndpoint\":\"https://api.voicepay.com/v1/payments/checkout\"}}," +
            "{\"id\":\"6\",\"type\":\"ivrNode\",\"position\":{\"x\":450,\"y\":580},\"data\":{\"label\":\"Agent Transfer\",\"description\":\"Connecting to human agent\",\"status\":\"pending\",\"icon\":\"Headset\",\"voicePrompt\":\"Estamos transfiriendo su llamada con el siguiente agente disponible. Por favor, no cuelgue.\",\"apiEndpoint\":\"https://api.voicepay.com/v1/agents/transfer\"}}," +
            "{\"id\":\"user-service\",\"type\":\"serviceNode\",\"position\":{\"x\":650,\"y\":180},\"data\":{\"label\":\"User Service\",\"icon\":\"User\",\"apiEndpoint\":\"https://api.voicepay.com/v1/users\"}}," +
            "{\"id\":\"payment-service\",\"type\":\"serviceNode\",\"position\":{\"x\":650,\"y\":310},\"data\":{\"label\":\"Payment Service\",\"icon\":\"CreditCard\",\"apiEndpoint\":\"https://api.voicepay.com/v1/payments\"}}," +
            "{\"id\":\"notification-service\",\"type\":\"serviceNode\",\"position\":{\"x\":650,\"y\":580},\"data\":{\"label\":\"Notif. Service\",\"icon\":\"Globe\",\"apiEndpoint\":\"https://api.voicepay.com/v1/notifications\"}}," +
            "{\"id\":\"agent-service\",\"type\":\"serviceNode\",\"position\":{\"x\":650,\"y\":700},\"data\":{\"label\":\"Human Agent\",\"icon\":\"Headset\",\"apiEndpoint\":\"https://api.voicepay.com/v1/agents\"}}" +
            "],\"edges\":[" +
            "{\"id\":\"e1-2\",\"source\":\"1\",\"target\":\"2\",\"animated\":false,\"style\":{\"stroke\":\"#4b5563\",\"strokeWidth\":2},\"markerEnd\":{\"type\":\"arrowclosed\",\"color\":\"#4b5563\"}}," +
            "{\"id\":\"e2-3\",\"source\":\"2\",\"target\":\"3\",\"animated\":false,\"style\":{\"stroke\":\"#4b5563\",\"strokeWidth\":2},\"markerEnd\":{\"type\":\"arrowclosed\",\"color\":\"#4b5563\"}}," +
            "{\"id\":\"e3-4\",\"source\":\"3\",\"target\":\"4\",\"animated\":false,\"style\":{\"stroke\":\"#4b5563\",\"strokeWidth\":2},\"markerEnd\":{\"type\":\"arrowclosed\",\"color\":\"#4b5563\"}}," +
            "{\"id\":\"e4-5\",\"source\":\"4\",\"target\":\"5\",\"label\":\"Option 1\",\"labelStyle\":{\"fill\":\"#71717a\",\"fontSize\":10,\"fontWeight\":700},\"animated\":false,\"style\":{\"stroke\":\"#4b5563\",\"strokeWidth\":2},\"markerEnd\":{\"type\":\"arrowclosed\",\"color\":\"#4b5563\"}}," +
            "{\"id\":\"e4-6\",\"source\":\"4\",\"target\":\"6\",\"label\":\"Option 2\",\"labelStyle\":{\"fill\":\"#71717a\",\"fontSize\":10,\"fontWeight\":700},\"animated\":false,\"style\":{\"stroke\":\"#4b5563\",\"strokeWidth\":2},\"markerEnd\":{\"type\":\"arrowclosed\",\"color\":\"#4b5563\"}}," +
            "{\"id\":\"comm-user\",\"source\":\"2\",\"target\":\"user-service\",\"animated\":false,\"style\":{\"stroke\":\"#3b82f6\",\"strokeWidth\":1,\"strokeDasharray\":\"5,5\",\"opacity\":0.3}}," +
            "{\"id\":\"comm-pay\",\"source\":\"3\",\"target\":\"payment-service\",\"animated\":false,\"style\":{\"stroke\":\"#3b82f6\",\"strokeWidth\":1,\"strokeDasharray\":\"5,5\",\"opacity\":0.3}}," +
            "{\"id\":\"comm-notif\",\"source\":\"5\",\"target\":\"notification-service\",\"animated\":false,\"style\":{\"stroke\":\"#3b82f6\",\"strokeWidth\":1,\"strokeDasharray\":\"5,5\",\"opacity\":0.3}}," +
            "{\"id\":\"comm-agent\",\"source\":\"6\",\"target\":\"agent-service\",\"animated\":false,\"style\":{\"stroke\":\"#3b82f6\",\"strokeWidth\":1,\"strokeDasharray\":\"5,5\",\"opacity\":0.3}}" +
            "]}";

    public com.voicepay.ivr.dto.IvrFlowConfig saveFlowConfig(String flowJson) {
        log.info("Saving IVR decision tree config...");
        com.voicepay.ivr.dto.IvrFlowConfig config = flowConfigRepository.findById("default")
                .orElse(com.voicepay.ivr.dto.IvrFlowConfig.builder().id("default").build());
        config.setFlowJson(flowJson);
        config.setUpdatedAt(java.time.LocalDateTime.now());
        return flowConfigRepository.save(config);
    }

    public com.voicepay.ivr.dto.IvrFlowConfig getFlowConfig() {
        log.info("Retrieving IVR decision tree config...");
        return flowConfigRepository.findById("default")
                .orElseGet(() -> {
                    log.info("No saved flow config found, returning default template...");
                    return com.voicepay.ivr.dto.IvrFlowConfig.builder()
                            .id("default")
                            .flowJson(DEFAULT_FLOW_JSON)
                            .updatedAt(java.time.LocalDateTime.now())
                            .build();
                });
    }

    private String getVoicePromptFromConfig(String nodeId, String defaultValue) {
        try {
            com.voicepay.ivr.dto.IvrFlowConfig config = flowConfigRepository.findById("default").orElse(null);
            String flowJson = config != null ? config.getFlowJson() : null;
            if (flowJson == null) {
                flowJson = DEFAULT_FLOW_JSON;
            }
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode rootNode = mapper.readTree(flowJson);
            com.fasterxml.jackson.databind.JsonNode nodesArray = rootNode.get("nodes");
            if (nodesArray != null && nodesArray.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode node : nodesArray) {
                    if (nodeId.equals(node.get("id").asText())) {
                        com.fasterxml.jackson.databind.JsonNode data = node.get("data");
                        if (data != null && data.get("voicePrompt") != null) {
                            String prompt = data.get("voicePrompt").asText();
                            if (prompt != null && !prompt.trim().isEmpty()) {
                                return prompt;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error reading dynamic voice prompt for node {}: {}", nodeId, e.getMessage());
        }
        return defaultValue;
    }

    public String processTwilioPayResult(Long userId, String callSid, String result, String paymentStatus, String paymentError, String chargeSid) {
        log.info("Processing Twilio Pay result: userId={}, callSid={}, result={}, paymentStatus={}, chargeSid={}", 
                userId, callSid, result, paymentStatus, chargeSid);

        LiveCall activeCall = liveCalls.get(callSid);

        if ("success".equalsIgnoreCase(result) && "complete".equalsIgnoreCase(paymentStatus)) {
            log.info("Twilio Pay Success for User ID: {}, ChargeSid: {}", userId, chargeSid);

            try {
                paymentServiceClient.confirmExternalPayment(userId, chargeSid, getHeadersWithJwt());

                if (activeCall != null) {
                    activeCall.setStatus("COMPLETED");
                    activeCall.getCallEvents().add("Pago seguro procesado con éxito vía Twilio Pay.");
                    activeCall.getCallEvents().add("Stripe Charge ID: " + chargeSid);
                    activeCall.setDuration(java.time.Duration.between(activeCall.getTimestamp(), java.time.LocalDateTime.now()).getSeconds());
                    callRepository.save(activeCall);
                    broadcaster.broadcast(liveCalls.values());

                    // Update CampaignMember status
                    updateCampaignMemberCallStatus(activeCall.getCampaignMemberId(), "COMPLETED");

                    final String sid = callSid;
                    new java.util.Timer().schedule(new java.util.TimerTask() {
                        @Override
                        public void run() {
                            liveCalls.remove(sid);
                            broadcaster.broadcast(liveCalls.values());
                        }
                    }, 5000);
                }

                String promptTemplate = getVoicePromptFromConfig("5", "Gracias. Su pago ha sido procesado correctamente. Le hemos enviado un mensaje de confirmación a su móvil. ¡Adiós!");
                String dynamicMessage = promptTemplate.replace("{name}", activeCall != null ? activeCall.getUserName() : "Cliente").replace("{amount}", activeCall != null ? String.valueOf(activeCall.getCallAmount()) : "25");

                return new VoiceResponse.Builder()
                        .say(new Say.Builder(dynamicMessage)
                                .language(Say.Language.ES_ES).build())
                        .hangup(new Hangup.Builder().build())
                        .build().toXml();

            } catch (Exception e) {
                log.error("Error confirming external payment: {}", e.getMessage());
            }
        }

        log.warn("Twilio Pay failed or canceled. Result: {}, PaymentStatus: {}, Error: {}", result, paymentStatus, paymentError);
        if (activeCall != null) {
            activeCall.setStatus("FAILED");
            activeCall.getCallEvents().add("Fallo en Twilio Pay: " + result + " (Error: " + paymentError + ")");
            callRepository.save(activeCall);
            broadcaster.broadcast(liveCalls.values());

            // Update CampaignMember status
            updateCampaignMemberCallStatus(activeCall.getCampaignMemberId(), "FAILED");

            final String sid = callSid;
            new java.util.Timer().schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    liveCalls.remove(sid);
                    broadcaster.broadcast(liveCalls.values());
                }
            }, 5000);
        }

        return new VoiceResponse.Builder()
                .say(new Say.Builder("Hubo un error al procesar el pago con su tarjeta. La operación ha sido cancelada. ¡Adiós!")
                        .language(Say.Language.ES_ES).build())
                .hangup(new Hangup.Builder().build())
                .build().toXml();
    }

    public String getTwilioAuthToken() {
        return twilioProperties.getAuthToken();
    }

    public void processPaymentCallbackAsync(Long userId, String callSid, String paymentToken) {
        log.info("Processing Twilio Pay callback asynchronously: userId={}, callSid={}, paymentToken={}", userId, callSid, paymentToken);

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            Long resolvedUserId = userId;
            String resolvedCallSid = callSid;

            // 1. Si no tenemos userId, intentamos resolverlo a través de CallSid -> LiveCall -> phoneNumber -> UserService
            if (resolvedUserId == null && resolvedCallSid != null) {
                try {
                    LiveCall activeCall = liveCalls.get(resolvedCallSid);
                    if (activeCall == null) {
                        activeCall = callRepository.findById(resolvedCallSid).orElse(null);
                    }
                    if (activeCall != null && activeCall.getPhoneNumber() != null) {
                        log.info("Resolving user ID via phone number: {}", activeCall.getPhoneNumber());
                        Map<String, Object> user = userServiceClient.getUserByPhone(activeCall.getPhoneNumber(), getHeadersWithJwt());
                        if (user != null && user.get("id") != null) {
                            resolvedUserId = ((Number) user.get("id")).longValue();
                            log.info("Resolved user ID: {} for call: {}", resolvedUserId, resolvedCallSid);
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to resolve user ID from CallSid {}: {}", resolvedCallSid, e.getMessage());
                }
            }

            if (resolvedUserId == null) {
                log.error("Cannot confirm payment: User ID is null and could not be resolved from callSid {}", resolvedCallSid);
                updateLiveCallStatus(resolvedCallSid, "FAILED", "Error: No se pudo identificar al usuario.");
                return;
            }

            try {
                log.info("Calling Payment Service to confirm external payment. User ID: {}, Token: {}", resolvedUserId, paymentToken);
                paymentServiceClient.confirmExternalPayment(resolvedUserId, paymentToken, getHeadersWithJwt());

                // Actualizar llamada a COMPLETED
                updateLiveCallStatus(resolvedCallSid, "COMPLETED", "Pago confirmado con éxito vía callback de Twilio.");
            } catch (Exception e) {
                log.error("Error calling Payment Service from Twilio callback: {}", e.getMessage());
                updateLiveCallStatus(resolvedCallSid, "FAILED", "Error al confirmar el pago en pasarela externa: " + e.getMessage());
            }
        });
    }

    private void updateLiveCallStatus(String callSid, String status, String eventMessage) {
        if (callSid == null) return;
        
        LiveCall activeCall = liveCalls.get(callSid);
        if (activeCall == null) {
            activeCall = callRepository.findById(callSid).orElse(null);
        }
        
        if (activeCall != null) {
            activeCall.setStatus(status);
            activeCall.getCallEvents().add(eventMessage);
            
            if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                activeCall.setDuration(java.time.Duration.between(activeCall.getTimestamp(), java.time.LocalDateTime.now()).getSeconds());
                
                final String sid = callSid;
                new java.util.Timer().schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        liveCalls.remove(sid);
                        localFailedSpeechAttempts.remove(sid);
                        broadcaster.broadcast(liveCalls.values());
                    }
                }, 5000);

                // Update CampaignMember status
                updateCampaignMemberCallStatus(activeCall.getCampaignMemberId(), status);
            }
            
            callRepository.save(activeCall);
            broadcaster.broadcast(liveCalls.values());
        }
    }

    private void updateCampaignMemberCallStatus(Long memberId, String callStatus) {
        if (memberId == null) return;
        try {
            log.info("Updating campaign member {} status to {} via user-service", memberId, callStatus);
            String mappedStatus = "NO_ANSWER";
            if ("COMPLETED".equals(callStatus)) {
                mappedStatus = "COMPLETED";
            } else if ("BUSY".equals(callStatus)) {
                mappedStatus = "BUSY";
            } else if ("PENDING_RETRY".equals(callStatus)) {
                mappedStatus = "PENDING";
            } else if ("RINGING".equals(callStatus) || "CONNECTED".equals(callStatus) || "IN_PROGRESS".equals(callStatus) || "PROCESSING_PAYMENT".equals(callStatus) || "WAITING_CONFIRMATION".equals(callStatus)) {
                mappedStatus = "RINGING";
            } else {
                mappedStatus = "NO_ANSWER";
            }
            userServiceClient.updateCampaignMemberStatus(memberId, mappedStatus, getHeadersWithJwt());
        } catch (Exception e) {
            log.error("Failed to update status for campaign member {} to {}: {}", memberId, callStatus, e.getMessage());
        }
    }

    public boolean transferBackToPaymentIvr(String callId) {
        log.info("Requesting transfer back to secure payment IVR for call ID: {}", callId);
        
        LiveCall activeCall = liveCalls.get(callId);
        if (activeCall == null) {
            activeCall = callRepository.findById(callId).orElse(null);
        }
        
        if (activeCall == null) {
            log.warn("Call with ID {} not found for transfer-back.", callId);
            return false;
        }

        // Update call details in dashboard
        activeCall.setStatus("PROCESSING_PAYMENT");
        activeCall.setSelectedOption("1");
        activeCall.getCallEvents().add("Agente inició transferencia segura de regreso al IVR de pago.");
        activeCall.getCallEvents().add("Iniciando cobro seguro vía Twilio Pay...");
        callRepository.save(activeCall);
        broadcaster.broadcast(liveCalls.values());

        // Determine if it is a real Twilio call or simulated
        boolean isTwilioCall = activeCall.getId() != null && !activeCall.getId().startsWith("SIM-");

        if (isTwilioCall) {
            try {
                // Fetch userId
                Long userId = 999L; // fallback
                try {
                    java.util.Map<String, Object> user = userServiceClient.getUserByPhone(activeCall.getPhoneNumber(), getHeadersWithJwt());
                    if (user != null && user.containsKey("id")) {
                        userId = ((Number) user.get("id")).longValue();
                    }
                } catch (Exception e) {
                    log.error("Error fetching user for transfer-back: {}", e.getMessage());
                }

                String domain = twilioProperties.getWebhookUrl();
                if (domain == null || domain.isEmpty()) {
                    domain = "http://localhost:8082";
                }
                if (domain.endsWith("/")) {
                    domain = domain.substring(0, domain.length() - 1);
                }

                // Construct TwiML redirect URL
                String redirectUrl = domain + "/ivr/twilio-webhook?userId=" + userId + "&callId=" + activeCall.getId() + "&Digits=1";
                log.info("Redirecting Twilio call {} to secure payment TwiML: {}", activeCall.getId(), redirectUrl);

                // Update Twilio call to redirect
                com.twilio.rest.api.v2010.account.Call.updater(activeCall.getId())
                        .setUrl(new java.net.URI(redirectUrl))
                        .setMethod(com.twilio.http.HttpMethod.POST)
                        .update();

                return true;
            } catch (Exception e) {
                log.error("Failed to redirect real Twilio call to payment: {}", e.getMessage());
                return false;
            }
        } else {
            // Simulated call: handled on frontend. The REST API just returns success.
            return true;
        }
    }

    public java.util.List<LiveCall> getCallHistory() {
        return callRepository.findAll();
    }

    public java.util.Collection<LiveCall> getLiveCalls() {
        return liveCalls.values();
    }
}

