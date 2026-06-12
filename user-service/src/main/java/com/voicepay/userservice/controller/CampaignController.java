package com.voicepay.userservice.controller;

import com.voicepay.userservice.dto.CampaignMemberDto;
import com.voicepay.userservice.model.CampaignMember;
import com.voicepay.userservice.repository.CampaignMemberRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/campaigns")
@RequiredArgsConstructor
@Tag(name = "Endpoint Campañas", description = "Gestión de campañas y miembros")
public class CampaignController {

    private final CampaignMemberRepository campaignMemberRepository;

    @GetMapping("/members/pending")
    @Operation(summary = "Obtener miembros pendientes", description = "Devuelve una página de miembros de campañas activas cuyo estado de llamada es PENDING.")
    public ResponseEntity<Page<CampaignMemberDto>> getPendingMembers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        log.info("Fetching pending campaign members - page: {}, size: {}", page, size);
        Pageable pageable = PageRequest.of(page, size);
        Page<CampaignMember> pendingMembers = campaignMemberRepository.findPendingMembersOfActiveCampaigns(pageable);
        
        Page<CampaignMemberDto> dtoPage = pendingMembers.map(this::toDto);
        return ResponseEntity.ok(dtoPage);
    }

    @PutMapping("/members/{id}/status")
    @Operation(summary = "Actualizar estado de llamada de un miembro", description = "Actualiza el estado de la llamada (PENDING, RINGING, COMPLETED, NO_ANSWER, BUSY) de un miembro de campaña.")
    public ResponseEntity<CampaignMemberDto> updateMemberStatus(
            @PathVariable long id,
            @RequestParam String status) {
        
        log.info("Updating campaign member {} status to {}", id, status);
        CampaignMember member = campaignMemberRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Campaign member not found: " + id));
        
        // Basic validation
        String upperStatus = status.trim().toUpperCase();
        if (!upperStatus.equals("PENDING") && !upperStatus.equals("RINGING") && 
            !upperStatus.equals("COMPLETED") && !upperStatus.equals("NO_ANSWER") && 
            !upperStatus.equals("BUSY")) {
            throw new IllegalArgumentException("Invalid call status: " + status);
        }
        
        member.setCallStatus(upperStatus);
        CampaignMember saved = campaignMemberRepository.save(member);
        return ResponseEntity.ok(toDto(saved));
    }

    private CampaignMemberDto toDto(CampaignMember member) {
        String timezone = (member.getUser() != null) ? member.getUser().getTimezone() : "Europe/Madrid";
        String userName = (member.getUser() != null) ? member.getUser().getName() : "Usuario Desconocido";
        Long userId = (member.getUser() != null) ? member.getUser().getId() : null;

        return CampaignMemberDto.builder()
                .id(member.getId())
                .campaignId(member.getCampaign() != null ? member.getCampaign().getId() : null)
                .userId(userId)
                .userName(userName)
                .phoneNumber(member.getPhoneNumber())
                .associatedDebt(member.getAssociatedDebt())
                .callStatus(member.getCallStatus())
                .timezone(timezone)
                .build();
    }
}
