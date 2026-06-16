package com.voicepay.userservice.service;

import com.voicepay.userservice.dto.CampaignMemberDto;
import com.voicepay.userservice.model.Campaign;
import com.voicepay.userservice.model.CampaignMember;
import com.voicepay.userservice.model.Commerce;
import com.voicepay.userservice.model.User;
import com.voicepay.userservice.repository.CampaignMemberRepository;
import com.voicepay.userservice.repository.CampaignRepository;
import com.voicepay.userservice.repository.CommerceRepository;
import com.voicepay.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignMemberRepository campaignMemberRepository;
    private final CommerceRepository commerceRepository;
    private final UserRepository userRepository;

    @Transactional
    public Campaign createCampaign(String name, Integer maxRetries, Long commerceId, List<CampaignMemberDto> memberDtos) {
        log.info("Creating campaign '{}' with commerce ID: {} and {} members", name, commerceId, memberDtos.size());
        
        // Find or create Commerce
        Commerce commerce;
        if (commerceId == null) {
            commerce = commerceRepository.findById(1L)
                    .orElseGet(() -> commerceRepository.save(Commerce.builder()
                            .name("Comercio Principal")
                            .email("principal@voicepay.com")
                            .build()));
        } else {
            commerce = commerceRepository.findById(commerceId)
                    .orElseGet(() -> commerceRepository.save(Commerce.builder()
                            .name("Comercio " + commerceId)
                            .email("commerce" + commerceId + "@voicepay.com")
                            .build()));
        }

        // Create Campaign
        Campaign campaign = Campaign.builder()
                .name(name)
                .startDate(LocalDateTime.now())
                .status("ACTIVE")
                .maxRetries(maxRetries != null ? maxRetries : 3)
                .commerce(commerce)
                .build();
        campaign = campaignRepository.save(campaign);

        // Process members
        for (CampaignMemberDto dto : memberDtos) {
            User user = null;
            if (dto.getUserId() != null) {
                user = userRepository.findById(dto.getUserId()).orElse(null);
            }
            if (user == null && dto.getPhoneNumber() != null) {
                // Try to find user by phone number
                user = userRepository.findByPhoneNumber(dto.getPhoneNumber()).orElse(null);
            }
            if (user == null) {
                // Create dummy user
                String phone = dto.getPhoneNumber() != null ? dto.getPhoneNumber() : "+0000000000";
                String email = "contacto_" + phone.replace("+", "").replace(" ", "").replace("-", "") + "_" + System.currentTimeMillis() + "@voicepay.com";
                user = User.builder()
                        .name(dto.getUserName() != null && !dto.getUserName().trim().isEmpty() ? dto.getUserName() : "Contacto " + phone)
                        .phoneNumber(phone)
                        .email(email)
                        .role("ROLE_USER")
                        .active(true)
                        .build();
                user = userRepository.save(user);
            }

            CampaignMember member = CampaignMember.builder()
                    .campaign(campaign)
                    .user(user)
                    .phoneNumber(dto.getPhoneNumber() != null ? dto.getPhoneNumber() : user.getPhoneNumber())
                    .associatedDebt(dto.getAssociatedDebt() != null ? dto.getAssociatedDebt() : 0.0)
                    .callStatus("PENDING")
                    .build();
            campaignMemberRepository.save(member);
        }

        return campaign;
    }

    @Transactional
    public Campaign updateCampaignStatus(Long campaignId, String status) {
        log.info("Updating campaign {} status to {}", campaignId, status);
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("Campaign not found: " + campaignId));
        
        String upperStatus = status.trim().toUpperCase();
        if (!upperStatus.equals("ACTIVE") && !upperStatus.equals("PAUSED") && 
            !upperStatus.equals("DRAFT") && !upperStatus.equals("COMPLETED")) {
            throw new IllegalArgumentException("Invalid campaign status: " + status);
        }
        
        campaign.setStatus(upperStatus);
        Campaign saved = campaignRepository.save(campaign);
        if (saved.getCommerce() != null) {
            saved.getCommerce().getName(); // force initialization of lazy proxy
        }
        return saved;
    }

    public Map<String, Object> getCampaignsReport() {
        log.info("Generating aggregated campaign report");
        List<Campaign> campaigns = campaignRepository.findAll();
        List<CampaignMember> members = campaignMemberRepository.findAll();

        long totalCampaigns = campaigns.size();
        long totalCalls = members.size();
        long completedCalls = members.stream().filter(m -> "COMPLETED".equalsIgnoreCase(m.getCallStatus())).count();
        long pendingCalls = members.stream().filter(m -> "PENDING".equalsIgnoreCase(m.getCallStatus()) || "RINGING".equalsIgnoreCase(m.getCallStatus()) || "PENDING_RETRY".equalsIgnoreCase(m.getCallStatus())).count();
        long failedCalls = members.stream().filter(m -> "FAILED".equalsIgnoreCase(m.getCallStatus()) || "NO_ANSWER".equalsIgnoreCase(m.getCallStatus()) || "BUSY".equalsIgnoreCase(m.getCallStatus())).count();

        double successRate = totalCalls > 0 ? ((double) completedCalls / totalCalls) * 100.0 : 0.0;

        List<Map<String, Object>> campaignsStats = new ArrayList<>();
        for (Campaign c : campaigns) {
            List<CampaignMember> cMembers = members.stream().filter(m -> m.getCampaign() != null && m.getCampaign().getId().equals(c.getId())).toList();
            long cTotal = cMembers.size();
            long cCompleted = cMembers.stream().filter(m -> "COMPLETED".equalsIgnoreCase(m.getCallStatus())).count();
            long cPending = cMembers.stream().filter(m -> "PENDING".equalsIgnoreCase(m.getCallStatus()) || "RINGING".equalsIgnoreCase(m.getCallStatus()) || "PENDING_RETRY".equalsIgnoreCase(m.getCallStatus())).count();
            long cFailed = cMembers.stream().filter(m -> "FAILED".equalsIgnoreCase(m.getCallStatus()) || "NO_ANSWER".equalsIgnoreCase(m.getCallStatus()) || "BUSY".equalsIgnoreCase(m.getCallStatus())).count();
            double cSuccessRate = cTotal > 0 ? ((double) cCompleted / cTotal) * 100.0 : 0.0;

            Map<String, Object> cStat = new HashMap<>();
            cStat.put("campaignId", c.getId());
            cStat.put("campaignName", c.getName());
            cStat.put("status", c.getStatus());
            cStat.put("totalMembers", cTotal);
            cStat.put("completedCalls", cCompleted);
            cStat.put("pendingCalls", cPending);
            cStat.put("failedCalls", cFailed);
            cStat.put("successRate", cSuccessRate);

            campaignsStats.add(cStat);
        }

        Map<String, Object> report = new HashMap<>();
        report.put("totalCampaigns", totalCampaigns);
        report.put("totalCalls", totalCalls);
        report.put("completedCalls", completedCalls);
        report.put("pendingCalls", pendingCalls);
        report.put("failedCalls", failedCalls);
        report.put("successRate", successRate);
        report.put("campaignsStats", campaignsStats);

        return report;
    }
}
