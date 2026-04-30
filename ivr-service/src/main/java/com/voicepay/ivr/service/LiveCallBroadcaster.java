package com.voicepay.ivr.service;

import com.voicepay.ivr.dto.LiveCall;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveCallBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Emite la lista actualizada de llamadas en vivo a todos los clientes
     * suscritos al canal /topic/live-calls.
     */
    public void broadcast(Collection<LiveCall> liveCalls) {
        log.debug("Broadcasting {} live calls to WebSocket clients", liveCalls.size());
        messagingTemplate.convertAndSend("/topic/live-calls", liveCalls);
    }
}
