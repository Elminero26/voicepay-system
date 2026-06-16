package com.voicepay.payment.controller;

import com.voicepay.payment.client.UserServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:campaigntestdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.defer-datasource-initialization=true",
    "spring.sql.init.mode=never",
    "app.user-service.url=http://localhost:8080/users",
    "app.notification-service.url=http://localhost:8083/notifications"
})
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings("null")
public class CampaignAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserServiceClient userServiceClient;

    @Test
    void createCampaign_WithValidCsv_ShouldReturnCreated() throws Exception {
        String csvContent = "userId,phoneNumber,associatedDebt\n2,+34600112233,150.00\n";
        MockMultipartFile file = new MockMultipartFile("file", "contacts.csv", "text/csv", csvContent.getBytes());

        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("id", 1L);
        mockResponse.put("name", "Campaña Test CSV");
        mockResponse.put("status", "ACTIVE");

        when(userServiceClient.createCampaign(any(), any(HttpHeaders.class))).thenReturn(mockResponse);

        mockMvc.perform(multipart("/api/campaigns")
                .file(file)
                .param("name", "Campaña Test CSV")
                .param("maxRetries", "3")
                .param("commerceId", "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Campaña Test CSV"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createCampaign_WithValidJson_ShouldReturnCreated() throws Exception {
        String jsonContent = "[{\"userId\": 2, \"phoneNumber\": \"+34600112233\", \"associatedDebt\": 150.00}]";
        MockMultipartFile file = new MockMultipartFile("file", "contacts.json", "application/json", jsonContent.getBytes());

        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("id", 2L);
        mockResponse.put("name", "Campaña Test JSON");
        mockResponse.put("status", "ACTIVE");

        when(userServiceClient.createCampaign(any(), any(HttpHeaders.class))).thenReturn(mockResponse);

        mockMvc.perform(multipart("/api/campaigns")
                .file(file)
                .param("name", "Campaña Test JSON"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("Campaña Test JSON"));
    }

    @Test
    void createCampaign_WithMissingName_ShouldReturnBadRequest() throws Exception {
        String csvContent = "userId,phoneNumber,associatedDebt\n2,+34600112233,150.00\n";
        MockMultipartFile file = new MockMultipartFile("file", "contacts.csv", "text/csv", csvContent.getBytes());

        mockMvc.perform(multipart("/api/campaigns")
                .file(file)
                .param("name", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El nombre de la campaña es obligatorio"));
    }

    @Test
    void createCampaign_WithEmptyFile_ShouldReturnBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "contacts.csv", "text/csv", new byte[0]);

        mockMvc.perform(multipart("/api/campaigns")
                .file(file)
                .param("name", "Campaña Vacía"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El archivo de contactos es obligatorio y no puede estar vacío"));
    }

    @Test
    void updateCampaignStatus_WithValidStatus_ShouldReturnOk() throws Exception {
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("id", 1L);
        mockResponse.put("status", "PAUSED");

        when(userServiceClient.updateCampaignStatus(eq(1L), eq("PAUSED"), any(HttpHeaders.class))).thenReturn(mockResponse);

        mockMvc.perform(put("/api/campaigns/1/status")
                .param("status", "PAUSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));
    }

    @Test
    void updateCampaignStatus_WithInvalidStatus_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(put("/api/campaigns/1/status")
                .param("status", "INVALID_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Estado no válido. Debe ser uno de: ACTIVE, PAUSED, DRAFT, COMPLETED"));
    }

    @Test
    void getCampaignsReport_ShouldReturnReport() throws Exception {
        Map<String, Object> mockReport = new HashMap<>();
        mockReport.put("totalCampaigns", 2L);
        mockReport.put("totalCalls", 10L);
        mockReport.put("completedCalls", 5L);

        when(userServiceClient.getCampaignsReport(any(HttpHeaders.class))).thenReturn(mockReport);

        mockMvc.perform(get("/api/campaigns/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCampaigns").value(2))
                .andExpect(jsonPath("$.totalCalls").value(10))
                .andExpect(jsonPath("$.completedCalls").value(5));
    }
}
