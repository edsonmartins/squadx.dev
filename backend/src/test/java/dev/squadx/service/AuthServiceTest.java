package dev.squadx.service;

import dev.squadx.dto.auth.*;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.User;
import dev.squadx.model.enums.UserRole;
import dev.squadx.repository.UserRepository;
import dev.squadx.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .password("encoded-password")
                .fullName("Test User")
                .role(UserRole.USER)
                .isActive(true)
                .emailVerified(false)
                .build();
        testUser.setId(1L);
        testUser.setCreatedAt(Instant.now());
    }

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("should register a new user successfully")
        void shouldRegisterSuccessfully() {
            RegisterRequest request = RegisterRequest.builder()
                    .email("new@example.com")
                    .password("password123")
                    .fullName("New User")
                    .build();

            when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
            when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                saved.setId(1L);
                saved.setCreatedAt(Instant.now());
                return saved;
            });
            when(jwtService.generateToken(any(User.class))).thenReturn("access-token");
            when(jwtService.generateRefreshToken(any(User.class))).thenReturn("refresh-token");
            when(jwtService.getExpiration()).thenReturn(3600000L);

            AuthResponse response = authService.register(request);

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
            assertThat(response.getExpiresIn()).isEqualTo(3600L);
            assertThat(response.getUser()).isNotNull();
            assertThat(response.getUser().getEmail()).isEqualTo("new@example.com");
            assertThat(response.getUser().getFullName()).isEqualTo("New User");

            verify(userRepository).existsByEmail("new@example.com");
            verify(userRepository).save(any(User.class));
            verify(passwordEncoder).encode("password123");
        }

        @Test
        @DisplayName("should throw BadRequestException when email is already registered")
        void shouldThrowWhenDuplicateEmail() {
            RegisterRequest request = RegisterRequest.builder()
                    .email("existing@example.com")
                    .password("password123")
                    .fullName("Existing User")
                    .build();

            when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Email already registered");

            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("should login successfully with valid credentials")
        void shouldLoginSuccessfully() {
            LoginRequest request = LoginRequest.builder()
                    .email("test@example.com")
                    .password("password123")
                    .build();

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(jwtService.generateToken(any(User.class))).thenReturn("access-token");
            when(jwtService.generateRefreshToken(any(User.class))).thenReturn("refresh-token");
            when(jwtService.getExpiration()).thenReturn(3600000L);

            AuthResponse response = authService.login(request);

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
            assertThat(response.getUser().getEmail()).isEqualTo("test@example.com");

            verify(authenticationManager).authenticate(
                    new UsernamePasswordAuthenticationToken("test@example.com", "password123")
            );
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("should throw when password is wrong")
        void shouldThrowWhenWrongPassword() {
            LoginRequest request = LoginRequest.builder()
                    .email("test@example.com")
                    .password("wrong-password")
                    .build();

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BadCredentialsException.class);

            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("refreshToken()")
    class RefreshToken {

        @Test
        @DisplayName("should refresh token successfully with valid refresh token")
        void shouldRefreshTokenSuccessfully() {
            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("valid-refresh-token")
                    .build();

            when(jwtService.extractUsername("valid-refresh-token")).thenReturn("test@example.com");
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(jwtService.isTokenValid("valid-refresh-token", testUser)).thenReturn(true);
            when(jwtService.generateToken(testUser)).thenReturn("new-access-token");
            when(jwtService.getExpiration()).thenReturn(3600000L);

            AuthResponse response = authService.refreshToken(request);

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo("new-access-token");
            assertThat(response.getRefreshToken()).isEqualTo("valid-refresh-token");
            assertThat(response.getExpiresIn()).isEqualTo(3600L);
        }

        @Test
        @DisplayName("should throw BadRequestException when refresh token is invalid")
        void shouldThrowWhenInvalidRefreshToken() {
            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("invalid-refresh-token")
                    .build();

            when(jwtService.extractUsername("invalid-refresh-token")).thenReturn("test@example.com");
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(jwtService.isTokenValid("invalid-refresh-token", testUser)).thenReturn(false);

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Invalid refresh token");
        }
    }

    @Nested
    @DisplayName("getCurrentUser()")
    class GetCurrentUser {

        @Test
        @DisplayName("should return user when found by email")
        void shouldReturnUserWhenFound() {
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            UserResponse response = authService.getCurrentUser("test@example.com");

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getEmail()).isEqualTo("test@example.com");
            assertThat(response.getFullName()).isEqualTo("Test User");
            assertThat(response.getRole()).isEqualTo(UserRole.USER);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.getCurrentUser("unknown@example.com"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("User not found");
        }
    }

    @Nested
    @DisplayName("updateProfile()")
    class UpdateProfile {

        @Test
        @DisplayName("should update full name and avatar URL")
        void shouldUpdateProfile() {
            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setFullName("Updated Name");
            request.setAvatarUrl("https://example.com/avatar.png");

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UserResponse response = authService.updateProfile("test@example.com", request);

            assertThat(response).isNotNull();
            assertThat(response.getFullName()).isEqualTo("Updated Name");
            assertThat(response.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");

            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("should only update non-null fields")
        void shouldOnlyUpdateNonNullFields() {
            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setFullName("Updated Name");
            // avatarUrl is null

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UserResponse response = authService.updateProfile("test@example.com", request);

            assertThat(response.getFullName()).isEqualTo("Updated Name");
            assertThat(response.getAvatarUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("changePassword()")
    class ChangePassword {

        @Test
        @DisplayName("should change password successfully when current password matches")
        void shouldChangePasswordSuccessfully() {
            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setCurrentPassword("current-password");
            request.setNewPassword("new-password123");

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("current-password", "encoded-password")).thenReturn(true);
            when(passwordEncoder.encode("new-password123")).thenReturn("new-encoded-password");

            authService.changePassword("test@example.com", request);

            verify(passwordEncoder).matches("current-password", "encoded-password");
            verify(passwordEncoder).encode("new-password123");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("should throw BadRequestException when current password is wrong")
        void shouldThrowWhenWrongCurrentPassword() {
            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setCurrentPassword("wrong-password");
            request.setNewPassword("new-password123");

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

            assertThatThrownBy(() -> authService.changePassword("test@example.com", request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Current password is incorrect");

            verify(userRepository, never()).save(any(User.class));
        }
    }
}
