package com.voicepay.ivr.scheduler;

import com.voicepay.ivr.client.UserServiceClient;
import com.voicepay.ivr.dto.CampaignMemberDto;
import com.voicepay.ivr.dto.LiveCall;
import com.voicepay.ivr.dto.PageResponse;
import com.voicepay.ivr.security.JwtUtil;
import com.voicepay.ivr.service.IvrService;
import com.voicepay.ivr.service.TimezoneValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Slf4j
@Component
@RequiredArgsConstructor
@DisallowConcurrentExecution
public class OutboundCallSchedulerJob implements Job {

    private final UserServiceClient userServiceClient;
    private final IvrService ivrService;
    private final TimezoneValidator timezoneValidator;
    private final JwtUtil jwtUtil;
    private final com.voicepay.ivr.config.AppProperties appProperties;

    private HttpHeaders getHeadersWithJwt() {
        HttpHeaders headers = new HttpHeaders();
        String token = jwtUtil.generateToken("ivr-service", "ROLE_ADMIN");
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("Starting outbound call scheduler execution tick...");

        int maxConcurrentCalls = appProperties.getScheduler().getMaxConcurrentCalls();
        int chunkSize = appProperties.getScheduler().getChunkSize();
        boolean forceMock = appProperties.getScheduler().isForceMock();

        try {
            // 1. Calculate active outbound calls
            Collection<LiveCall> liveCalls = ivrService.getLiveCalls();
            long activeOutboundCalls = liveCalls.stream()
                    .filter(c -> "OUTBOUND".equals(c.getDirection()) && 
                            ("CONNECTED".equals(c.getStatus()) || 
                             "WAITING_CONFIRMATION".equals(c.getStatus()) || 
                             "PROCESSING_PAYMENT".equals(c.getStatus())))
                    .count();

            log.info("Current active outbound calls: {}/{}", activeOutboundCalls, maxConcurrentCalls);

            if (activeOutboundCalls >= maxConcurrentCalls) {
                log.info("Max concurrency limit reached. Skipping this execution.");
                return;
            }

            long availableCapacity = maxConcurrentCalls - activeOutboundCalls;
            int fetchSize = Math.min(chunkSize, (int) availableCapacity);

            if (fetchSize <= 0) {
                log.info("No capacity available for placing new calls.");
                return;
            }

            // 2. Fetch pending campaign members (lote/chunk)
            log.info("Fetching a chunk of {} pending contacts...", fetchSize);
            PageResponse<CampaignMemberDto> pendingMembersPage = userServiceClient.getPendingCampaignMembers(0, fetchSize, getHeadersWithJwt());

            if (pendingMembersPage == null || pendingMembersPage.getContent() == null || pendingMembersPage.getContent().isEmpty()) {
                log.info("No pending campaign members found.");
                return;
            }

            // 3. Process the chunk
            int startedCallsCount = 0;
            for (CampaignMemberDto member : pendingMembersPage.getContent()) {
                if (activeOutboundCalls + startedCallsCount >= maxConcurrentCalls) {
                    log.info("Reached max concurrency limit while processing chunk.");
                    break;
                }

                log.info("Processing member: {} (Phone: {}, Timezone: {})", member.getUserName(), member.getPhoneNumber(), member.getTimezone());

                // Validation of timezone/local time commercial window
                if (!timezoneValidator.isWithinCommercialHours(member.getTimezone())) {
                    log.info("Skipping member {} due to legal calling hours restriction", member.getUserName());
                    continue;
                }

                // 4. Update status to RINGING to reserve and prevent double calling
                try {
                    userServiceClient.updateCampaignMemberStatus(member.getId(), "RINGING", getHeadersWithJwt());
                } catch (Exception e) {
                    log.error("Failed to update status to RINGING for member {}. Skipping call. Error: {}", member.getId(), e.getMessage());
                    continue;
                }

                // 5. Trigger outbound call
                try {
                    log.info("Triggering call to {} (MemberId: {})", member.getPhoneNumber(), member.getId());
                    ivrService.triggerOutboundCall(member.getPhoneNumber(), member.getId(), forceMock);
                    startedCallsCount++;
                } catch (Exception e) {
                    log.error("Failed to trigger outbound call to {}: {}", member.getPhoneNumber(), e.getMessage());
                    // Revert status to PENDING so it can be retried
                    try {
                        userServiceClient.updateCampaignMemberStatus(member.getId(), "PENDING", getHeadersWithJwt());
                    } catch (Exception ex) {
                        log.error("Failed to revert status to PENDING for member {}: {}", member.getId(), ex.getMessage());
                    }
                }
            }

            log.info("Outbound call scheduler tick finished. Started {} new calls.", startedCallsCount);

        } catch (Exception e) {
            log.error("Error executing outbound call scheduler job: {}", e.getMessage(), e);
            throw new JobExecutionException(e);
        }
    }
}
