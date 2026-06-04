package com.voicepay.notification;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(
		info = @Info(title = "Notification Service API", version = "1.0", description = "Microservicio para el envío de notificaciones por SMS, Correo Electrónico y notificaciones Push en tiempo real."),
		servers = @io.swagger.v3.oas.annotations.servers.Server(url = "${app.gateway.url:http://localhost:9000}", description = "API Gateway"),
		security = @SecurityRequirement(name = "BearerAuth")
)
@SecurityScheme(
		name = "BearerAuth",
		type = SecuritySchemeType.HTTP,
		scheme = "bearer",
		bearerFormat = "JWT",
		description = "Ingrese el token JWT obtenido del login para autenticar las peticiones."
)
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
