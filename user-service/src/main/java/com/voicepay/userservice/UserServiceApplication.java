package com.voicepay.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.voicepay.userservice.config.AppProperties;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@org.springframework.scheduling.annotation.EnableScheduling
@OpenAPIDefinition(
		info = @Info(title = "User Service API", version = "1.0", description = "Microservicio para la gestión de usuarios, roles y autenticación (incluyendo OAuth2 y JWT)."),
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
public class UserServiceApplication implements WebMvcConfigurer {

	public static void main(String[] args) {
		SpringApplication.run(UserServiceApplication.class, args);
	}

	@Override
	public void addCorsMappings(@org.springframework.lang.NonNull CorsRegistry registry) {
		registry.addMapping("/**")
				.allowedOrigins("http://localhost:5173", "http://localhost:3000")
				.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
				.allowedHeaders("*")
				.allowCredentials(false);
	}
}
