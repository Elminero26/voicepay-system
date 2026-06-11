package com.voicepay.userservice.repository;

import com.voicepay.userservice.model.CampaignMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CampaignMemberRepository extends JpaRepository<CampaignMember, Long> {
    List<CampaignMember> findByCampaignId(Long campaignId);
    List<CampaignMember> findByUserId(Long userId);
}
