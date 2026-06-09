package com.voicepay.payment.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    private String currency;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "Periodicity is required")
    private Periodicity periodicity;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    private LocalDateTime lastPaymentDate;

    @NotNull(message = "Next payment date is required")
    private LocalDateTime nextPaymentDate;

    private String description;

    private String gatewayCustomerToken;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = SubscriptionStatus.ACTIVE;
        }
    }

    public enum Periodicity {
        DAILY, WEEKLY, MONTHLY, YEARLY
    }

    public enum SubscriptionStatus {
        ACTIVE, CANCELLED
    }
}
