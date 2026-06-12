package com.voicepay.ivr.scheduler;

import com.voicepay.ivr.client.UserServiceClient;
import com.voicepay.ivr.dto.CampaignMemberDto;
import com.voicepay.ivr.dto.LiveCall;
import com.voicepay.ivr.dto.PageResponse;
import com.voicepay.ivr.security.JwtUtil;
import com.voicepay.ivr.service.IvrService;
import com.voicepay.ivr.service.TimezoneValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobExecutionContext;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OutboundCallSchedulerJobTest {

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private IvrService ivrService;

    @Mock
    private TimezoneValidator timezoneValidator;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private JobExecutionContext jobExecutionContext;

    private OutboundCallSchedulerJob job;

    @BeforeEach
    void setUp() {
        com.voicepay.ivr.config.AppProperties properties = new com.voicepay.ivr.config.AppProperties();
        properties.getScheduler().setChunkSize(5);
        properties.getScheduler().setMaxConcurrentCalls(5);
        properties.getScheduler().setForceMock(true);
        
        job = new OutboundCallSchedulerJob(userServiceClient, ivrService, timezoneValidator, jwtUtil, properties);
        
        lenient().when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("mock-token");
    }

    @Test
    void testExecute_NoCapacity_DoesNotCallUserService() throws Exception {
        // Arrange: 5 active outbound calls (max capacity)
        LiveCall c1 = LiveCall.builder().id("1").direction("OUTBOUND").status("CONNECTED").build();
        LiveCall c2 = LiveCall.builder().id("2").direction("OUTBOUND").status("CONNECTED").build();
        LiveCall c3 = LiveCall.builder().id("3").direction("OUTBOUND").status("CONNECTED").build();
        LiveCall c4 = LiveCall.builder().id("4").direction("OUTBOUND").status("CONNECTED").build();
        LiveCall c5 = LiveCall.builder().id("5").direction("OUTBOUND").status("CONNECTED").build();
        
        when(ivrService.getLiveCalls()).thenReturn(Arrays.asList(c1, c2, c3, c4, c5));

        // Act
        job.execute(jobExecutionContext);

        // Assert
        verify(userServiceClient, never()).getPendingCampaignMembers(anyInt(), anyInt(), any());
    }

    @Test
    void testExecute_HasCapacity_TriggersCallsForValidTimezone() throws Exception {
        // Arrange: 3 active outbound calls (2 available slots)
        LiveCall c1 = LiveCall.builder().id("1").direction("OUTBOUND").status("CONNECTED").build();
        LiveCall c2 = LiveCall.builder().id("2").direction("OUTBOUND").status("CONNECTED").build();
        LiveCall c3 = LiveCall.builder().id("3").direction("OUTBOUND").status("CONNECTED").build();
        when(ivrService.getLiveCalls()).thenReturn(Arrays.asList(c1, c2, c3));

        // Mock 2 pending members from User Service
        CampaignMemberDto m1 = CampaignMemberDto.builder()
                .id(101L)
                .userName("User 1")
                .phoneNumber("+34600111222")
                .timezone("Europe/Madrid")
                .build();
        CampaignMemberDto m2 = CampaignMemberDto.builder()
                .id(102L)
                .userName("User 2")
                .phoneNumber("+34600222333")
                .timezone("America/New_York")
                .build();

        PageResponse<CampaignMemberDto> pageResponse = new PageResponse<>();
        pageResponse.setContent(Arrays.asList(m1, m2));

        // Since capacity is 2 and chunk-size is 5, it should request up to 2 (Math.min(5, 2))
        when(userServiceClient.getPendingCampaignMembers(eq(0), eq(2), any())).thenReturn(pageResponse);

        // Timezone validator: m1 is within hours, m2 is outside hours
        when(timezoneValidator.isWithinCommercialHours("Europe/Madrid")).thenReturn(true);
        when(timezoneValidator.isWithinCommercialHours("America/New_York")).thenReturn(false);

        // Act
        job.execute(jobExecutionContext);

        // Assert:
        // Verify m1 status is updated and call triggered
        verify(userServiceClient, times(1)).updateCampaignMemberStatus(eq(101L), eq("RINGING"), any());
        verify(ivrService, times(1)).triggerOutboundCall(eq("+34600111222"), eq(101L), eq(true));

        // Verify m2 is skipped
        verify(userServiceClient, never()).updateCampaignMemberStatus(eq(102L), eq("RINGING"), any());
        verify(ivrService, never()).triggerOutboundCall(eq("+34600222333"), anyLong(), anyBoolean());
    }
}
