package com.voicepay.userservice.security;

import com.voicepay.userservice.model.User;
import com.voicepay.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = oAuth2User.getAttributes();
        
        String email = (String) attributes.get("email");
        if (email == null && "microsoft".equals(registrationId)) {
            // For Microsoft, email might be in 'mail' or 'userPrincipalName'
            email = (String) attributes.get("mail");
            if (email == null) {
                email = (String) attributes.get("userPrincipalName");
            }
        }

        if (email == null) {
            throw new OAuth2AuthenticationException("Email not found from OAuth2 provider");
        }

        String name = (String) attributes.get("name");
        if (name == null) {
            name = (String) attributes.get("displayName");
        }
        
        String providerId = oAuth2User.getName(); // Usually the unique ID from provider

        updateOrCreateUser(email, name, registrationId, providerId);

        return oAuth2User;
    }

    private void updateOrCreateUser(String email, String name, String provider, String providerId) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        
        User user;
        if (userOptional.isPresent()) {
            user = userOptional.get();
            user.setName(name);
            user.setProvider(provider);
            user.setProviderId(providerId);
        } else {
            user = User.builder()
                    .email(email)
                    .name(name)
                    .provider(provider)
                    .providerId(providerId)
                    .role("ROLE_USER")
                    .active(true)
                    .phoneNumber("N/A") // Placeholder since OAuth2 doesn't always provide phone
                    .password(null) // No password for OAuth2 users
                    .build();
        }
        userRepository.save(user);
    }
}
