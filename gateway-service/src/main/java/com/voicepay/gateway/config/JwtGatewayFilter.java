package com.voicepay.gateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.lang.NonNull;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class JwtGatewayFilter implements WebFilter {

    private final JwtUtil jwtUtil;

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().toString();

        // Permitir rutas públicas, WebSockets y documentación (OpenAPI/Swagger/Redoc)
        if (path.equals("/") || path.equals("/index.html") || 
            path.startsWith("/docs") || path.startsWith("/redoc") ||
            path.contains("/v3/api-docs") || path.startsWith("/swagger-ui") || 
            path.startsWith("/webjars") ||
            path.startsWith("/auth") || path.startsWith("/login") || path.startsWith("/oauth2") || 
            path.startsWith("/actuator") || path.startsWith("/h2-console") || 
            path.startsWith("/ws")) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.isTokenValid(token)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }
}
