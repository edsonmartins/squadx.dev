package dev.squadx.service;

import dev.squadx.model.User;
import dev.squadx.model.enums.UserRole;
import dev.squadx.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Custom OIDC user service that maps OIDC user info to the existing User model.
 * Supports JIT (Just-In-Time) provisioning: auto-creates users on first SSO login.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOidcUserService extends OidcUserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String email = oidcUser.getEmail();
        String subject = oidcUser.getSubject();
        String fullName = oidcUser.getFullName();
        String avatarUrl = oidcUser.getPicture();

        if (email == null) {
            log.error("SSO login failed: no email claim from provider {}", registrationId);
            throw new OAuth2AuthenticationException("Email claim is required for SSO login");
        }

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            // JIT provisioning: create user on first SSO login
            log.info("JIT provisioning new user via SSO: email={}, provider={}", email, registrationId);
            user = User.builder()
                    .email(email)
                    .password(UUID.randomUUID().toString()) // placeholder, SSO users don't use password
                    .fullName(fullName != null ? fullName : email)
                    .avatarUrl(avatarUrl)
                    .role(UserRole.USER)
                    .ssoProvider(registrationId)
                    .ssoSubjectId(subject)
                    .isActive(true)
                    .emailVerified(true) // SSO-verified email
                    .build();
            user = userRepository.save(user);
        } else {
            // Update SSO fields and profile info from provider
            log.debug("Existing user logging in via SSO: email={}, provider={}", email, registrationId);
            user.setSsoProvider(registrationId);
            user.setSsoSubjectId(subject);
            user.setLastLoginAt(Instant.now());

            if (fullName != null && !fullName.isBlank()) {
                user.setFullName(fullName);
            }
            if (avatarUrl != null && !avatarUrl.isBlank()) {
                user.setAvatarUrl(avatarUrl);
            }

            user = userRepository.save(user);
        }

        return oidcUser;
    }
}
