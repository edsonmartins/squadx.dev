package dev.squadx.config;

import dev.squadx.model.User;
import dev.squadx.repository.UserRepository;
import dev.squadx.security.JwtService;
import dev.squadx.service.CustomOidcUserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * OAuth2/OIDC configuration for SSO login.
 * Supports Google, Microsoft, Okta, and custom OIDC providers.
 * On successful SSO authentication, issues a JWT and redirects to the frontend.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class OAuth2Config {

    private final CustomOidcUserService customOidcUserService;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    /**
     * After successful OAuth2/OIDC login, look up the user by email,
     * generate a JWT, and redirect to the frontend with the token.
     */
    @Bean
    public AuthenticationSuccessHandler oauth2SuccessHandler() {
        return (request, response, authentication) -> {
            String email = null;

            if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.oidc.user.OidcUser oidcUser) {
                email = oidcUser.getEmail();
            } else if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User oauth2User) {
                email = oauth2User.getAttribute("email");
            }

            if (email == null) {
                log.error("OAuth2 login succeeded but no email found in principal");
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Email not available from SSO provider");
                return;
            }

            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                log.error("OAuth2 login succeeded but user not found in database: {}", email);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "User not provisioned");
                return;
            }

            String accessToken = jwtService.generateToken(user);
            String refreshToken = jwtService.generateRefreshToken(user);

            // Redirect to frontend with tokens
            String frontendUrl = allowedOrigins.split(",")[0].trim();
            String redirectUrl = frontendUrl + "/auth/sso-callback"
                    + "?token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8)
                    + "&refreshToken=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8);

            log.info("SSO login successful for {}, redirecting to frontend", email);
            response.sendRedirect(redirectUrl);
        };
    }
}
