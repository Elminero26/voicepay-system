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

    /**
     * Emite una transcripción de voz en tiempo real al frontend
     */
    public void broadcastTranscription(String callSid, String role, String text) {
        log.debug("Broadcasting transcription for call {}: [{}] {}", callSid, role, text);
        java.util.Map<String, String> payload = java.util.Map.of(
            "callSid", callSid,
            "role", role,
            "text", text
        );
        messagingTemplate.convertAndSend("/topic/transcriptions", payload);
        messagingTemplate.convertAndSend("/topic/calls/" + callSid + "/transcriptions", payload);
    }
}
