package com.voicepay.ivr.repository;

import com.voicepay.ivr.dto.IvrFlowConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IvrFlowConfigRepository extends JpaRepository<IvrFlowConfig, String> {
}
