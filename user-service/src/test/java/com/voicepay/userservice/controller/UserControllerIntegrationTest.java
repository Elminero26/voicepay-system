package com.voicepay.userservice.controller;

import com.voicepay.userservice.model.User;
import com.voicepay.userservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
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
}
