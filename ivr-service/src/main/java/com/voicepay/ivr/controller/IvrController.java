package com.voicepay.ivr.controller;

import com.voicepay.ivr.dto.CallRequest;
import com.voicepay.ivr.dto.IvrResponse;
import com.voicepay.ivr.service.IvrService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ivr")
@RequiredArgsConstructor
public class IvrController {

    private final IvrService ivrService;

    @PostMapping("/call")
    public IvrResponse handleCall(@Valid @RequestBody CallRequest request) {
        return ivrService.handleIncomingCall(request);
    }

    @PostMapping("/confirm-payment")
    public IvrResponse confirmPayment(@RequestParam Long userId) {
        return ivrService.confirmPayment(userId);
    }

    @GetMapping("/calls/live")
    public java.util.Collection<com.voicepay.ivr.dto.LiveCall> getLiveCalls() {
        return ivrService.getLiveCalls();
    }
}
