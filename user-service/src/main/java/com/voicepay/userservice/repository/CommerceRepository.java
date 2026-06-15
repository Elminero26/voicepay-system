package com.voicepay.userservice.repository;

import com.voicepay.userservice.model.Commerce;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceRepository extends JpaRepository<Commerce, Long> {
}
