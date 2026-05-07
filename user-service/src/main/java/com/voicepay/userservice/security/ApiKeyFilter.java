package com.voicepay.userservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    @Value("${app.api.key}")
    private String apiKey;

    private static final String API_KEY_HEADER = "X-API-KEY";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, 
                                    @NonNull HttpServletResponse response, 
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String requestApiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey.equals(requestApiKey)) {
            // Le damos el rol de ADMIN a quien use la llave correcta
            java.util.List<org.springframework.security.core.GrantedAuthority> authorities = 
                org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_ADMIN");

            org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken authentication =
                    new org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken(
                            "ApiKeyUser", null, authorities);
            
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
            
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid or missing API Key");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // Excluimos Swagger y consola H2 si es necesario para desarrollo
        return path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs") || path.startsWith("/h2-console");
    }
}
