package com.voicepay.ivr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {
    private String recipient;
    private String message;
    private String type; // SMS, EMAIL, PUSH
    private String status; // PENDING, SENT, FAILED
}
