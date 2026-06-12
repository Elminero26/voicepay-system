package com.voicepay.ivr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CampaignMemberDto {
    private Long id;
    private Long campaignId;
    private Long userId;
    private String userName;
    private String phoneNumber;
    private Double associatedDebt;
    private String callStatus;
    private String timezone;
}
