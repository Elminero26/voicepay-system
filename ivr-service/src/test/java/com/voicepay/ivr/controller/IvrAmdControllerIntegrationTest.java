package com.voicepay.ivr.controller;

import com.voicepay.ivr.service.IvrService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.defer-datasource-initialization=true",
    "spring.sql.init.mode=never",
    "app.user-service.url=http://localhost:8080/users",
    "app.payment-service.url=http://localhost:8081/payments",
    "twilio.account-sid=AC_DUMMY_SID_FOR_TESTS",
    "twilio.auth-token=DUMMY_TOKEN_FOR_TESTS",
    "twilio.phone-number=+10000000000",
    "twilio.webhook-url=http://localhost:8082"
})
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings("null")
public class IvrAmdControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IvrService ivrService;

    @Test
    void handleTwilioAmdCallback_ShouldReturnTwiML() throws Exception {
        String mockTwiML = "<Response><Hangup/></Response>";
        when(ivrService.handleTwilioAmdCallback(anyString(), anyString(), anyString(), anyString())).thenReturn(mockTwiML);

        mockMvc.perform(post("/ivr/twilio-amd-callback")
                .param("From", "+34777777777")
                .param("CallSid", "CA123456789")
                .param("AnsweredBy", "machine_start"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(mockTwiML));
    }
}
