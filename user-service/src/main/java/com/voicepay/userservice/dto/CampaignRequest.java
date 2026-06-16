package com.voicepay.userservice.dto;

import lombok.Data;
import java.util.List;

@Data
public class CampaignRequest {
    private String name;
    private Integer maxRetries;
    private Long commerceId;
    private List<CampaignMemberDto> members;
}
