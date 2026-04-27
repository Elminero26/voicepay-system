package com.voicepay.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentStats {
    private long completed;
    private long failed;
    private long pending;
    private BigDecimal totalAmount;
}
