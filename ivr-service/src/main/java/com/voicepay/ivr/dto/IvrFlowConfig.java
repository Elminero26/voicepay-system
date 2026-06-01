package com.voicepay.ivr.dto;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "ivr_flow_config")
public class IvrFlowConfig {
    @Id
    private String id; // "default"

    @Column(columnDefinition = "TEXT")
    private String flowJson;

    private LocalDateTime updatedAt;
}
