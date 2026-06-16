package com.voicepay.userservice.model;

import com.voicepay.userservice.repository.CampaignMemberRepository;
import com.voicepay.userservice.repository.CampaignRepository;
import com.voicepay.userservice.repository.CommerceRepository;
import com.voicepay.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:campaign_testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.defer-datasource-initialization=false",
    "spring.sql.init.mode=never",
    "spring.flyway.enabled=true",
    "spring.flyway.baseline-on-migrate=true",
    "spring.flyway.baseline-version=1",
    "spring.security.oauth2.client.registration.google.client-id=dummy-id",
    "spring.security.oauth2.client.registration.google.client-secret=dummy-secret",
    "spring.security.oauth2.client.registration.google.scope=profile,email"
})
@Transactional
@SuppressWarnings("null")
public class CampaignEntitiesTest {

    @Autowired
    private CommerceRepository commerceRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private CampaignMemberRepository campaignMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.voicepay.userservice.service.CampaignService campaignService;

    @Test
    void testUpdateCampaignStatus() {
        Commerce commerce = Commerce.builder()
                .name("Comercio De Prueba")
                .email("test@commerce.com")
                .build();
        commerce = commerceRepository.save(commerce);

        Campaign campaign = Campaign.builder()
                .name("Campaña Cobro Julio")
                .startDate(LocalDateTime.now())
                .status("ACTIVE")
                .maxRetries(3)
                .commerce(commerce)
                .build();
        campaign = campaignRepository.save(campaign);

        Campaign updated = campaignService.updateCampaignStatus(campaign.getId(), "PAUSED");
        assertEquals("PAUSED", updated.getStatus());
    }

    @Test
    void testCreateCommerceCampaignAndMember() {
        // 1. Create a Commerce
        Commerce commerce = Commerce.builder()
                .name("Comercio De Prueba")
                .email("test@commerce.com")
                .build();
        commerce = commerceRepository.save(commerce);
        assertNotNull(commerce.getId());

        // 2. Create a Campaign
        Campaign campaign = Campaign.builder()
                .name("Campaña Cobro Julio")
                .startDate(LocalDateTime.now())
                .status("ACTIVE")
                .maxRetries(3)
                .commerce(commerce)
                .build();
        campaign = campaignRepository.save(campaign);
        assertNotNull(campaign.getId());

        // 3. Create a User (Member)
        User user = User.builder()
                .name("Carlos Miembro")
                .email("carlos.miembro@test.com")
                .phoneNumber("+34699887755")
                .role("ROLE_USER")
                .active(true)
                .build();
        user = userRepository.save(user);
        assertNotNull(user.getId());

        // 4. Create a CampaignMember
        CampaignMember member = CampaignMember.builder()
                .campaign(campaign)
                .user(user)
                .phoneNumber(user.getPhoneNumber())
                .associatedDebt(150.00)
                .callStatus("PENDING")
                .build();
        member = campaignMemberRepository.save(member);
        assertNotNull(member.getId());

        // 5. Query and Assert
        List<Campaign> campaigns = campaignRepository.findByCommerceId(commerce.getId());
        assertEquals(1, campaigns.size());
        assertEquals("Campaña Cobro Julio", campaigns.get(0).getName());

        List<CampaignMember> members = campaignMemberRepository.findByCampaignId(campaign.getId());
        assertEquals(1, members.size());
        assertEquals("+34699887755", members.get(0).getPhoneNumber());
        assertEquals(150.00, members.get(0).getAssociatedDebt());
        assertEquals("PENDING", members.get(0).getCallStatus());
    }
}
