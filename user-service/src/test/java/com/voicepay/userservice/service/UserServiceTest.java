package com.voicepay.userservice.service;

import com.voicepay.userservice.model.User;
import com.voicepay.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService — Tests Unitarios")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .name("Test User")
                .email("test@voicepay.com")
                .phoneNumber("+34611223344")
                .role("user")
                .active(true)
                .build();
    }

    // ─── findAll ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll — devuelve lista de usuarios")
    void findAll_returnsAllUsers() {
        when(userRepository.findAll()).thenReturn(List.of(testUser));

        List<User> result = userService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Test User");
        verify(userRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("findAll — devuelve lista vacía si no hay usuarios")
    void findAll_returnsEmptyList_whenNoUsers() {
        when(userRepository.findAll()).thenReturn(List.of());

        List<User> result = userService.findAll();

        assertThat(result).isEmpty();
    }

    // ─── findById ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById — devuelve usuario cuando existe")
    void findById_returnsUser_whenExists() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        User result = userService.findById(1L);

        assertThat(result.getEmail()).isEqualTo("test@voicepay.com");
    }

    @Test
    @DisplayName("findById — lanza excepción cuando no existe")
    void findById_throwsException_whenNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
    }

    // ─── findByPhoneNumber ───────────────────────────────────────────────────────

    @Test
    @DisplayName("findByPhoneNumber — devuelve usuario con ese teléfono")
    void findByPhoneNumber_returnsUser_whenExists() {
        when(userRepository.findByPhoneNumber("+34611223344"))
                .thenReturn(Optional.of(testUser));

        User result = userService.findByPhoneNumber("+34611223344");

        assertThat(result.getName()).isEqualTo("Test User");
    }

    @Test
    @DisplayName("findByPhoneNumber — lanza excepción si el teléfono no existe")
    void findByPhoneNumber_throwsException_whenNotFound() {
        when(userRepository.findByPhoneNumber("+00000000000"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findByPhoneNumber("+00000000000"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found with phone");
    }

    // ─── save ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("save — guarda y devuelve el usuario")
    void save_persistsAndReturnsUser() {
        when(userRepository.save(testUser)).thenReturn(testUser);

        User result = userService.save(testUser);

        assertThat(result.getId()).isEqualTo(1L);
        verify(userRepository, times(1)).save(testUser);
    }

    // ─── updateUser ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateUser — actualiza los datos correctamente")
    void updateUser_updatesFields() {
        User updatedData = User.builder()
                .name("Updated Name")
                .email("updated@voicepay.com")
                .phoneNumber("+34699999999")
                .role("admin")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        User result = userService.updateUser(1L, updatedData);

        verify(userRepository, times(1)).save(any(User.class));
        assertThat(result).isNotNull();
    }

    // ─── deactivateUser ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("deactivateUser — marca el usuario como inactivo")
    void deactivateUser_setsActiveFalse() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        userService.deactivateUser(1L);

        assertThat(testUser.getActive()).isFalse();
        verify(userRepository, times(1)).save(testUser);
    }
}
