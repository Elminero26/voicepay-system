package com.voicepay.userservice.repository;

import com.voicepay.userservice.model.CampaignMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CampaignMemberRepository extends JpaRepository<CampaignMember, Long> {
    List<CampaignMember> findByCampaignId(Long campaignId);
    List<CampaignMember> findByUserId(Long userId);

    @Query("SELECT cm FROM CampaignMember cm JOIN cm.campaign c WHERE cm.callStatus = 'PENDING' AND c.status = 'ACTIVE'")
    Page<CampaignMember> findPendingMembersOfActiveCampaigns(Pageable pageable);
}
