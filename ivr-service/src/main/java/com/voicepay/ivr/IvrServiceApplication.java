package com.voicepay.ivr;

import com.voicepay.ivr.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;

import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
@EnableConfigurationProperties(AppProperties.class)
@OpenAPIDefinition(
		info = @Info(title = "IVR Service API", version = "1.0", description = "Microservicio para gestionar el árbol de decisión interactivo del IVR, controlar llamadas Twilio reales y simuladas, registrar eventos en vivo de Speech-To-Text y almacenar el historial."),
		servers = @Server(url = "${app.gateway.url:http://localhost:9000}", description = "API Gateway"),
		security = @SecurityRequirement(name = "BearerAuth")
)
@SecurityScheme(
		name = "BearerAuth",
		type = SecuritySchemeType.HTTP,
		scheme = "bearer",
		bearerFormat = "JWT",
		description = "Ingrese el token JWT obtenido del login para autenticar las peticiones."
)
public class IvrServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(IvrServiceApplication.class, args);
    }
}
