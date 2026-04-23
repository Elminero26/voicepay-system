package com.voicepay.ivr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IvrResponse {
    private String message;
    private String nextAction;
    private Long userId;
}
