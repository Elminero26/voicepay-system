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

    @PostMapping("/confirm-payment")
    @Operation(summary = "Confirmar pago en llamada", description = "Simula que el usuario confirma el pago durante la llamada IVR. Desencadena la comunicación con el Payment Service.")
    public IvrResponse confirmPayment(@RequestParam Long userId) {
        return ivrService.confirmPayment(userId);
    }

    @RequestMapping(value = "/twilio-call", method = {RequestMethod.GET, RequestMethod.POST}, produces = "application/xml")
    @Operation(summary = "Endpoint para Twilio", description = "Responde a las llamadas reales de Twilio usando TwiML.")
    public String handleTwilioCall(@RequestParam("From") String from) {
        return ivrService.handleTwilioCall(from);
    }

    @RequestMapping(value = "/twilio-webhook", method = {RequestMethod.GET, RequestMethod.POST}, produces = "application/xml")
    @Operation(summary = "Capturar dígitos Twilio", description = "Procesa la tecla pulsada por el usuario en el teléfono.")
    public String handleTwilioWebhook(
            @RequestParam("userId") Long userId,
            @RequestParam("callId") String callId,
            @RequestParam(value = "Digits", required = false) String digits) {
        return ivrService.handleTwilioWebhook(userId, callId, digits != null ? digits : "");
    }

    @GetMapping("/calls/live")
    @Operation(summary = "Obtener llamadas en vivo", description = "Devuelve el estado actual de todas las llamadas en progreso en el sistema IVR.")
    public java.util.Collection<com.voicepay.ivr.dto.LiveCall> getLiveCalls() {
        return ivrService.getLiveCalls();
    }
}
