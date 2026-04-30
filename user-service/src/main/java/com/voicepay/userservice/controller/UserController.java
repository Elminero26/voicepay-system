package com.voicepay.userservice.controller;

import com.voicepay.userservice.model.User;
import com.voicepay.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Endpoint usuarios", description = "CRUD básico /users")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Obtener todos los usuarios", description = "Devuelve una lista con todos los usuarios registrados en el sistema.")
    public List<User> getAllUsers() {
        return userService.findAll();
    }

    @PostMapping
    @Operation(summary = "Crear nuevo usuario", description = "Registra un nuevo usuario en la base de datos.")
    public User createUser(@Valid @RequestBody User user) {
        return userService.save(user);
    }

    @GetMapping("/search")
    @Operation(summary = "Buscar usuarios por nombre", description = "Devuelve una lista de usuarios cuyo nombre coincida con el parámetro de búsqueda.")
    public List<User> searchByName(@RequestParam String name) {
        return userService.findByName(name);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener usuario por ID", description = "Busca y devuelve un usuario específico utilizando su identificador único.")
    public User getUserById(@PathVariable Long id) {
        return userService.findById(id);
    }

    @GetMapping("/phone/{phoneNumber}")
    @Operation(summary = "Buscar usuario por teléfono", description = "Busca un usuario utilizando su número de teléfono exacto.")
    public User getUserByPhone(@PathVariable String phoneNumber) {
        return userService.findByPhoneNumber(phoneNumber);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar usuario", description = "Modifica los datos de un usuario existente.")
    public User updateUser(@PathVariable Long id, @Valid @RequestBody User user) {
        return userService.updateUser(id, user);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desactivar usuario", description = "Realiza un borrado lógico (desactiva) del usuario en el sistema.")
    public void deleteUser(@PathVariable Long id) {
        userService.deactivateUser(id);
    }
}
