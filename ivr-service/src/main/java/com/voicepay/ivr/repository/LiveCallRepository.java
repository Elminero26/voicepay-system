package com.voicepay.ivr.repository;

import com.voicepay.ivr.dto.LiveCall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LiveCallRepository extends JpaRepository<LiveCall, String> {
}
