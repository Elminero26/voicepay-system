package com.voicepay.userservice.controller;

import com.voicepay.userservice.model.User;
import com.voicepay.userservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:user_ctrl_testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.defer-datasource-initialization=true",
    "spring.sql.init.mode=never",
    "spring.flyway.enabled=false",
    "spring.security.oauth2.client.registration.google.client-id=dummy-id",
    "spring.security.oauth2.client.registration.google.client-secret=dummy-secret",
    "spring.security.oauth2.client.registration.google.scope=profile,email"
})
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings("null")
public class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @Test
    void getUserByPhone_ShouldReturnUser() throws Exception {
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setName("Richard");
        mockUser.setPhoneNumber("+34642297705");

        when(userService.findByPhoneNumber("+34642297705")).thenReturn(mockUser);

        mockMvc.perform(get("/users/phone/+34642297705"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Richard"))
                .andExpect(jsonPath("$.phoneNumber").value("+34642297705"));
    }

    @Test
    void createUser_ShouldReturnCreatedUser() throws Exception {
        User userToCreate = User.builder()
                .name("Nuevo Usuario")
                .email("nuevo@test.com")
                .phoneNumber("+34600000000")
                .role("user")
                .build();

        User savedUser = User.builder()
                .id(2L)
                .name("Nuevo Usuario")
                .email("nuevo@test.com")
                .phoneNumber("+34600000000")
                .role("user")
                .build();

        when(userService.save(org.mockito.ArgumentMatchers.any(User.class))).thenReturn(savedUser);

        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userToCreate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("Nuevo Usuario"));
    }

    @Test
    void getUserById_NotFound_ShouldReturnEmpty() throws Exception {
        // En un entorno real, el service podría lanzar una excepción o devolver null
        // Si el controller devuelve null, Spring por defecto devuelve 200 OK vacío o error dependiendo de la config
        when(userService.findById(999L)).thenReturn(null);

        mockMvc.perform(get("/users/999"))
                .andExpect(status().isOk()); // Depende de cómo manejes el null en el Controller
    }
}
