package dev.squadx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.squadx.dto.auth.*;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.model.User;
import dev.squadx.model.enums.UserRole;
import dev.squadx.security.JwtAuthenticationFilter;
import dev.squadx.security.JwtService;
import dev.squadx.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = AuthController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserDetailsService userDetailsService;

    private AuthResponse sampleAuthResponse;
    private UserResponse sampleUserResponse;

    @BeforeEach
    void setUp() {
        sampleUserResponse = UserResponse.builder()
                .id(1L)
                .email("test@example.com")
                .fullName("Test User")
                .role(UserRole.USER)
                .isActive(true)
                .emailVerified(false)
                .createdAt(Instant.now())
                .build();

        sampleAuthResponse = AuthResponse.builder()
                .accessToken("access-token-value")
                .refreshToken("refresh-token-value")
                .expiresIn(3600L)
                .user(sampleUserResponse)
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/auth/register")
    class RegisterEndpoint {

        @Test
        @DisplayName("should register successfully and return 201")
        void shouldRegisterSuccessfully() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .email("new@example.com")
                    .password("password123")
                    .fullName("New User")
                    .build();

            when(authService.register(any(RegisterRequest.class))).thenReturn(sampleAuthResponse);

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Registration successful"))
                    .andExpect(jsonPath("$.data.access_token").value("access-token-value"))
                    .andExpect(jsonPath("$.data.refresh_token").value("refresh-token-value"))
                    .andExpect(jsonPath("$.data.user.email").value("test@example.com"));
        }

        @Test
        @DisplayName("should return 400 when email is already registered")
        void shouldReturn400WhenDuplicateEmail() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .email("existing@example.com")
                    .password("password123")
                    .fullName("Existing User")
                    .build();

            when(authService.register(any(RegisterRequest.class)))
                    .thenThrow(new BadRequestException("Email already registered"));

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Email already registered"));
        }

        @Test
        @DisplayName("should return 400 when request validation fails")
        void shouldReturn400WhenValidationFails() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .email("invalid-email")
                    .password("short")
                    .fullName("")
                    .build();

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class LoginEndpoint {

        @Test
        @DisplayName("should login successfully and return 200")
        void shouldLoginSuccessfully() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .email("test@example.com")
                    .password("password123")
                    .build();

            when(authService.login(any(LoginRequest.class))).thenReturn(sampleAuthResponse);

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Login successful"))
                    .andExpect(jsonPath("$.data.access_token").value("access-token-value"))
                    .andExpect(jsonPath("$.data.refresh_token").value("refresh-token-value"));
        }

        @Test
        @DisplayName("should return 401 when credentials are invalid")
        void shouldReturn401WhenBadCredentials() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .email("test@example.com")
                    .password("wrong-password")
                    .build();

            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Invalid email or password"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/auth/me")
    class GetCurrentUserEndpoint {

        @Test
        @DisplayName("should return current user when authenticated")
        void shouldReturnCurrentUser() throws Exception {
            User authenticatedUser = User.builder()
                    .email("test@example.com")
                    .password("encoded")
                    .fullName("Test User")
                    .role(UserRole.USER)
                    .build();
            authenticatedUser.setId(1L);

            when(authService.getCurrentUser("test@example.com")).thenReturn(sampleUserResponse);

            mockMvc.perform(get("/api/v1/auth/me")
                            .with(user(authenticatedUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.email").value("test@example.com"))
                    .andExpect(jsonPath("$.data.full_name").value("Test User"))
                    .andExpect(jsonPath("$.data.role").value("USER"));
        }

        @Test
        @DisplayName("should fail when no authentication principal is provided")
        void shouldFailWhenNotAuthenticated() throws Exception {
            // With security filters disabled, calling /me without a user principal
            // results in a null @AuthenticationPrincipal, causing an error.
            // This verifies the endpoint cannot function without an authenticated user.
            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isInternalServerError());
        }
    }
}
