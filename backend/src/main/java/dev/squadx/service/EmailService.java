package dev.squadx.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
@Slf4j
public class EmailService {

    private final RestClient restClient;
    private final String apiKey;
    private final String fromEmail;

    public EmailService(
            @Value("${resend.api-key:}") String apiKey,
            @Value("${resend.from-email:noreply@squadx.dev}") String fromEmail
    ) {
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .build();
    }

    @PostConstruct
    void validateConfiguration() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("========================================================");
            log.warn("RESEND_API_KEY is not configured (resend.api-key is empty).");
            log.warn("Email sending is DISABLED. All emails will be silently skipped.");
            log.warn("Set RESEND_API_KEY environment variable to enable email delivery.");
            log.warn("========================================================");
        } else {
            log.info("Email service initialized with from={}", fromEmail);
        }
    }

    @Async
    public void sendWelcomeEmail(String to, String name) {
        String subject = "Welcome to SquadX!";
        String html = """
                <h1>Welcome to SquadX, %s!</h1>
                <p>Your AI Development Squad is ready to go. Start by creating your first project.</p>
                <p>Best regards,<br>The SquadX Team</p>
                """.formatted(name);

        sendEmail(to, subject, html);
    }

    @Async
    public void sendTaskCompletedEmail(String to, String taskTitle) {
        String subject = "Task Completed: " + taskTitle;
        String html = """
                <h2>Task Completed</h2>
                <p>The task <strong>%s</strong> has been completed successfully.</p>
                <p>Log in to SquadX to review the results.</p>
                <p>Best regards,<br>The SquadX Team</p>
                """.formatted(taskTitle);

        sendEmail(to, subject, html);
    }

    @Async
    public void sendApprovalRequestEmail(String to, String approverName, String taskTitle) {
        String subject = "Approval Required: " + taskTitle;
        String html = """
                <h2>Approval Request</h2>
                <p>Hi %s,</p>
                <p>Your approval is needed for the task <strong>%s</strong>.</p>
                <p>Please log in to SquadX to review and approve or reject this request.</p>
                <p>Best regards,<br>The SquadX Team</p>
                """.formatted(approverName, taskTitle);

        sendEmail(to, subject, html);
    }

    @Async
    public void sendPasswordResetEmail(String to, String resetLink) {
        String subject = "Reset Your SquadX Password";
        String html = """
                <h2>Password Reset</h2>
                <p>You requested a password reset. Click the link below to set a new password:</p>
                <p><a href="%s">Reset Password</a></p>
                <p>If you did not request this, please ignore this email.</p>
                <p>Best regards,<br>The SquadX Team</p>
                """.formatted(resetLink);

        sendEmail(to, subject, html);
    }

    private void sendEmail(String to, String subject, String html) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Resend API key is not configured. Skipping email to={}, subject={}", to, subject);
            return;
        }

        try {
            Map<String, Object> payload = Map.of(
                    "from", fromEmail,
                    "to", new String[]{to},
                    "subject", subject,
                    "html", html
            );

            restClient.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Email sent successfully to={}, subject={}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to={}, subject={}: {}", to, subject, e.getMessage(), e);
        }
    }
}
