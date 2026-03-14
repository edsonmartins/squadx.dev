package dev.squadx.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Nested
    @DisplayName("constructor and configuration")
    class Configuration {

        @Test
        @DisplayName("should create service with blank API key without throwing")
        void shouldCreateWithBlankApiKey() {
            assertThatCode(() -> new EmailService("", "noreply@squadx.dev"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should create service with valid API key")
        void shouldCreateWithValidApiKey() {
            assertThatCode(() -> new EmailService("re_test_key", "noreply@squadx.dev"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("sendWelcomeEmail()")
    class SendWelcomeEmail {

        @Test
        @DisplayName("should skip sending when API key is blank")
        void shouldSkipWhenApiKeyBlank() {
            EmailService service = new EmailService("", "noreply@squadx.dev");

            // Should not throw -- silently skips when API key is not configured
            assertThatCode(() -> service.sendWelcomeEmail("user@example.com", "Test User"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should skip sending when API key is null")
        void shouldSkipWhenApiKeyNull() {
            EmailService service = new EmailService(null, "noreply@squadx.dev");

            assertThatCode(() -> service.sendWelcomeEmail("user@example.com", "Test User"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("sendTaskCompletedEmail()")
    class SendTaskCompletedEmail {

        @Test
        @DisplayName("should skip sending when API key is not configured")
        void shouldSkipWhenNotConfigured() {
            EmailService service = new EmailService("", "noreply@squadx.dev");

            assertThatCode(() -> service.sendTaskCompletedEmail("user@example.com", "My Task"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("sendPasswordResetEmail()")
    class SendPasswordResetEmail {

        @Test
        @DisplayName("should skip sending when API key is not configured")
        void shouldSkipWhenNotConfigured() {
            EmailService service = new EmailService("  ", "noreply@squadx.dev");

            assertThatCode(() -> service.sendPasswordResetEmail("user@example.com", "https://reset.link"))
                    .doesNotThrowAnyException();
        }
    }
}
