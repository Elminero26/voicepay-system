package com.voicepay.ivr.controller;

import com.voicepay.ivr.dto.CallRequest;
import com.voicepay.ivr.dto.IvrResponse;
import com.voicepay.ivr.service.IvrService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/calls/live")
    @Operation(summary = "Obtener llamadas en vivo", description = "Devuelve el estado actual de todas las llamadas en progreso en el sistema IVR.")
    public java.util.Collection<com.voicepay.ivr.dto.LiveCall> getLiveCalls() {
        return ivrService.getLiveCalls();
    }
}
