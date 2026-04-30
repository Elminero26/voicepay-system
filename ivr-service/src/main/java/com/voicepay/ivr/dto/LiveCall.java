package com.voicepay.ivr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LiveCall {
    private String id;
    private String phoneNumber;
    private String userName;
    private String status; // CONNECTED, WAITING_CONFIRMATION, COMPLETED, FAILED
    private LocalDateTime timestamp;

    public long getDurationSeconds() {
        return java.time.Duration.between(timestamp, LocalDateTime.now()).getSeconds();
    }
}
