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

    @RequestMapping(value = "/twilio-webhook", method = {RequestMethod.GET, RequestMethod.POST}, produces = "application/xml")
    @Operation(summary = "Capturar dígitos Twilio", description = "Procesa la tecla pulsada por el usuario en el teléfono.")
    public String handleTwilioWebhook(
            @RequestParam("userId") Long userId,
            @RequestParam("callId") String callId,
            @RequestParam(value = "Digits", required = false) String digits,
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
        return ivrService.handleTwilioWebhook(userId, callId, digits != null ? digits : "", baseUrl);
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

    @GetMapping("/calls/history")
    @Operation(summary = "Obtener historial de llamadas", description = "Recupera todas las llamadas registradas en la base de datos de PostgreSQL.")
    public java.util.List<com.voicepay.ivr.dto.LiveCall> getCallHistory() {
        return ivrService.getCallHistory();
    }
}
