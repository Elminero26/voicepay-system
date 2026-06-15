package com.voicepay.userservice.repository;

import com.voicepay.userservice.model.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    List<Campaign> findByCommerceId(Long commerceId);
}
