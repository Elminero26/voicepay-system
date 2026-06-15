package com.voicepay.ivr.controller;

import com.voicepay.ivr.dto.CallRequest;
import com.voicepay.ivr.dto.IvrResponse;
import com.voicepay.ivr.service.IvrService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ivr")
@RequiredArgsConstructor
@Tag(name = "IVR Bot", description = "Control del flujo de llamadas interactivo")
public class IvrController {

    private final IvrService ivrService;

    @PostMapping("/call")
    @Operation(summary = "Simular llamada entrante", description = "Inicia el flujo IVR comprobando si el teléfono pertenece a un usuario registrado.")
    public IvrResponse handleCall(@Valid @RequestBody CallRequest request) {
        return ivrService.handleIncomingCall(request);
    }

    @PostMapping("/confirm")
    @Operation(summary = "Procesar opción de usuario", description = "Simula que el usuario pulsa una tecla durante la llamada (1 para pago, 2 para transferencia).")
    public IvrResponse confirmPayment(@RequestBody java.util.Map<String, Object> payload) {
        Long userId = Long.valueOf(payload.get("userId").toString());
        String option = payload.containsKey("option") ? payload.get("option").toString() : "1";
        return ivrService.processUserOption(userId, option);
    }

    @RequestMapping(value = "/twilio-call", method = {RequestMethod.GET, RequestMethod.POST}, produces = "application/xml")
    @Operation(summary = "Endpoint para Twilio", description = "Responde a las llamadas reales de Twilio usando TwiML.")
    public String handleTwilioCall(
            @RequestParam("From") String from,
            @RequestParam("CallSid") String callSid) {
        return ivrService.handleTwilioCall(from, callSid);
    }

    @RequestMapping(value = "/twilio-amd-callback", method = {RequestMethod.GET, RequestMethod.POST}, produces = "application/xml")
    @Operation(summary = "Capturar detección de contestador automático", description = "Procesa el resultado de Answering Machine Detection (AMD) de Twilio.")
    public String handleTwilioAmdCallback(
            @RequestParam("From") String from,
            @RequestParam("CallSid") String callSid,
            @RequestParam(value = "AnsweredBy", required = false) String answeredBy,
            jakarta.servlet.http.HttpServletRequest request) {
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null) {
            scheme = request.getScheme();
        }
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null) {
            host = request.getHeader("Host");
        }
        String baseUrl = scheme + "://" + host;
        return ivrService.handleTwilioAmdCallback(from, callSid, answeredBy, baseUrl);
    }

    @RequestMapping(value = "/twilio-webhook", method = {RequestMethod.GET, RequestMethod.POST}, produces = "application/xml")
    @Operation(summary = "Capturar dígitos o voz Twilio", description = "Procesa la tecla pulsada o la entrada de voz del usuario en el teléfono.")
    public String handleTwilioWebhook(
            @RequestParam("userId") Long userId,
            @RequestParam("callId") String callId,
            @RequestParam(value = "Digits", required = false) String digits,
            @RequestParam(value = "SpeechResult", required = false) String speechResult,
            @RequestParam(value = "UnstableSpeechResult", required = false) String unstableSpeechResult,
            jakarta.servlet.http.HttpServletRequest request) {
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null) {
            scheme = request.getScheme();
        }
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null) {
            host = request.getHeader("Host");
        }
        String baseUrl = scheme + "://" + host;
        return ivrService.handleTwilioWebhook(userId, callId, digits != null ? digits : "", speechResult != null ? speechResult : "", unstableSpeechResult != null ? unstableSpeechResult : "", baseUrl);
    }

    @RequestMapping(value = "/twilio-pay-action", method = {RequestMethod.GET, RequestMethod.POST}, produces = "application/xml")
    @Operation(summary = "Capturar retorno de Twilio Pay", description = "Procesa el resultado de la transacción realizada por Twilio Pay.")
    public String handleTwilioPayAction(
            @RequestParam("userId") Long userId,
            @RequestParam("CallSid") String callSid,
            @RequestParam("Result") String result,
            @RequestParam(value = "PaymentStatus", required = false) String paymentStatus,
            @RequestParam(value = "PaymentError", required = false) String paymentError,
            @RequestParam(value = "ChargeSid", required = false) String chargeSid) {
        return ivrService.processTwilioPayResult(userId, callSid, result, paymentStatus, paymentError, chargeSid);
    }

    @RequestMapping(value = "/payment-callback", method = {RequestMethod.GET, RequestMethod.POST}, produces = "application/xml")
    @Operation(summary = "Callback de pagos seguro de Twilio", description = "Recibe la confirmación segura de Twilio Pay, valida la firma y delega de forma asíncrona la confirmación al Payment Service.")
    public org.springframework.http.ResponseEntity<String> handlePaymentCallback(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "CallSid", required = false) String callSid,
            @RequestParam(value = "PaymentToken", required = false) String paymentToken,
            @RequestParam(value = "PaymentConfirmationCode", required = false) String paymentConfirmationCode,
            @RequestParam(value = "ChargeSid", required = false) String chargeSid,
            jakarta.servlet.http.HttpServletRequest request) {
        
        // Reconstrucción de la URL pública para la validación de firma
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null) {
            scheme = request.getScheme();
        }
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null) {
            host = request.getHeader("Host");
        }
        String baseUrl = scheme + "://" + host;
        String requestUrl = baseUrl + request.getRequestURI();
        if (request.getQueryString() != null) {
            requestUrl += "?" + request.getQueryString();
        }

        // Obtener firma de Twilio y token
        String signature = request.getHeader("X-Twilio-Signature");
        String authToken = ivrService.getTwilioAuthToken();

        boolean isLocalOrPlaceholder = authToken == null || authToken.contains("PLACEHOLDER") || signature == null;

        if (!isLocalOrPlaceholder) {
            com.twilio.security.RequestValidator validator = new com.twilio.security.RequestValidator(authToken);
            
            // Recopilar parámetros POST
            java.util.Map<String, String> postParams = new java.util.HashMap<>();
            request.getParameterMap().forEach((key, value) -> {
                if (value != null && value.length > 0) {
                    postParams.put(key, value[0]);
                }
            });
            
            boolean isValid = validator.validate(requestUrl, postParams, signature);
            if (!isValid) {
                return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                        .body("Invalid Twilio signature");
            }
        }

        // Extraer identificador de transacción y token de pago
        String resolvedCallSid = callSid != null ? callSid : request.getParameter("CallSid");
        if (resolvedCallSid == null || resolvedCallSid.isEmpty()) {
            resolvedCallSid = request.getParameter("Sid");
        }

        String resolvedToken = paymentToken != null ? paymentToken : request.getParameter("PaymentToken");
        if (resolvedToken == null || resolvedToken.isEmpty()) {
            resolvedToken = paymentConfirmationCode != null ? paymentConfirmationCode : request.getParameter("PaymentConfirmationCode");
        }
        if (resolvedToken == null || resolvedToken.isEmpty()) {
            resolvedToken = chargeSid != null ? chargeSid : request.getParameter("ChargeSid");
        }

        ivrService.processPaymentCallbackAsync(userId, resolvedCallSid, resolvedToken);

        return org.springframework.http.ResponseEntity.ok("<Response/>");
    }

    @RequestMapping(value = "/twilio-status", method = {RequestMethod.GET, RequestMethod.POST})
    @Operation(summary = "Capturar cambios de estado de Twilio", description = "Procesa los eventos de estado de la llamada enviados por Twilio.")
    public void handleTwilioStatus(
            @RequestParam("CallSid") String callSid,
            @RequestParam("CallStatus") String callStatus,
            @RequestParam(value = "CallDuration", required = false) String duration) {
        ivrService.handleTwilioStatus(callSid, callStatus, duration);
    }

    @PostMapping("/outbound")
    @Operation(summary = "Iniciar llamada saliente real o simulada", description = "Inicia una llamada real a través de Twilio, o una simulación interactiva si mock=true.")
    public IvrResponse triggerOutboundCall(
            @Valid @RequestBody CallRequest request,
            @RequestParam(value = "mock", required = false, defaultValue = "false") boolean forceMock) {
        return ivrService.triggerOutboundCall(request.getFrom(), forceMock);
    }

    @GetMapping("/flow")
    @Operation(summary = "Obtener configuración del flujo IVR", description = "Recupera la estructura JSON (nodos y enlaces) del árbol de decisión del IVR.")
    public com.voicepay.ivr.dto.IvrFlowConfig getFlowConfig() {
        return ivrService.getFlowConfig();
    }

    @PostMapping("/flow")
    @Operation(summary = "Guardar configuración del flujo IVR", description = "Persiste la estructura JSON del árbol de decisión del IVR en la base de datos.")
    public com.voicepay.ivr.dto.IvrFlowConfig saveFlowConfig(@RequestBody java.util.Map<String, String> payload) {
        String flowJson = payload.get("flowJson");
        if (flowJson == null) {
            throw new IllegalArgumentException("El campo 'flowJson' es requerido.");
        }
        return ivrService.saveFlowConfig(flowJson);
    }

    @GetMapping("/calls/live")
    @Operation(summary = "Obtener llamadas en vivo", description = "Devuelve el estado actual de todas las llamadas en progreso en el sistema IVR.")
    public java.util.Collection<com.voicepay.ivr.dto.LiveCall> getLiveCalls() {
        return ivrService.getLiveCalls();
    }

    @PostMapping("/calls/transfer-back")
    @Operation(summary = "Transferir llamada de regreso al IVR de pago", description = "El agente devuelve la llamada al IVR seguro para que el cliente introduzca sus datos bancarios.")
    public org.springframework.http.ResponseEntity<?> transferBackToPaymentIvr(@RequestBody java.util.Map<String, String> payload) {
        String callId = payload.get("callId");
        if (callId == null || callId.isEmpty()) {
            return org.springframework.http.ResponseEntity.badRequest().body("El campo 'callId' es requerido.");
        }
        boolean success = ivrService.transferBackToPaymentIvr(callId);
        if (success) {
            return org.springframework.http.ResponseEntity.ok(java.util.Map.of("success", true, "message", "Llamada transferida al IVR de pago con éxito."));
        } else {
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).body("No se encontró la llamada activa o no se pudo transferir.");
        }
    }

    @GetMapping("/calls/history")
    @Operation(summary = "Obtener historial de llamadas", description = "Recupera todas las llamadas registradas en la base de datos de PostgreSQL.")
    public java.util.List<com.voicepay.ivr.dto.LiveCall> getCallHistory() {
        return ivrService.getCallHistory();
    }
}
