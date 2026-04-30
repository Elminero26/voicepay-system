package com.voicepay.ivr.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CallRequest {
    @NotBlank(message = "Phone number is required")
    private String from;
}
