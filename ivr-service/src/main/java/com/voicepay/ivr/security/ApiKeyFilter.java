package com.voicepay.ivr.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
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
            PreAuthenticatedAuthenticationToken authentication =
                    new PreAuthenticatedAuthenticationToken(
                            "ApiKeyUser", null, AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } else {
            // Si no hay API Key, dejamos pasar para que JwtAuthFilter o permitAll decidan
            filterChain.doFilter(request, response);
        }
    }
}
