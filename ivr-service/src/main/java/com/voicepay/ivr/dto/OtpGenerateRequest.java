package com.voicepay.ivr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpGenerateRequest {
    private String identifier;
    private Integer length;
    private Integer ttlMinutes;
}
