package dev.squadx.service;

import dev.squadx.dto.notification.NotificationConfigRequest;
import dev.squadx.dto.notification.NotificationConfigResponse;
import dev.squadx.event.AgentStateChangedEvent;
import dev.squadx.event.ExecutionCompletedEvent;
import dev.squadx.event.TaskStatusChangedEvent;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.NotificationChannel;
import dev.squadx.model.NotificationConfig;
import dev.squadx.model.Organization;
import dev.squadx.model.enums.ExecutionStatus;
import dev.squadx.model.enums.TaskStatus;
import dev.squadx.notification.NotificationProviderRegistry;
import dev.squadx.repository.NotificationConfigRepository;
import dev.squadx.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationConfigRepository notificationConfigRepository;
    private final OrganizationRepository organizationRepository;
    private final NotificationProviderRegistry providerRegistry;

    // ── Domain Event Listeners ──────────────────────────────

    @TransactionalEventListener
    public void onTaskStatusChanged(TaskStatusChangedEvent event) {
        if (event.newStatus() == TaskStatus.DONE) {
            notifyTaskCompleted(event.organizationId(),
                    event.task() != null ? event.task().getTitle() : "Task #" + event.taskId(),
                    event.task() != null ? event.task().getProjectName() : "");
        } else if (event.newStatus() == TaskStatus.BLOCKED) {
            notifyTaskFailed(event.organizationId(),
                    event.task() != null ? event.task().getTitle() : "Task #" + event.taskId(),
                    "Task is blocked");
        }
    }

    @TransactionalEventListener
    public void onExecutionCompleted(ExecutionCompletedEvent event) {
        if (event.status() == ExecutionStatus.FAILED) {
            notifyTaskFailed(event.organizationId(), "Execution #" + event.executionId(), "Execution failed");
        }
    }

    @TransactionalEventListener
    public void onAgentStateChanged(AgentStateChangedEvent event) {
        if ("DEAD".equals(event.newState())) {
            notifyAgentError(event.organizationId(), "Agent #" + event.agentId(), "Agent is dead (no heartbeat)");
        }
    }

    // ── Config CRUD ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<NotificationConfigResponse> listByOrganization(Long orgId) {
        return notificationConfigRepository.findByOrganizationId(orgId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NotificationConfigResponse create(Long orgId, NotificationConfigRequest request) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + orgId));

        NotificationChannel channel = parseChannel(request.getChannel());

        if (notificationConfigRepository.existsByOrganizationIdAndChannel(orgId, channel)) {
            throw new BadRequestException("Notification config already exists for channel: " + channel);
        }

        validateWebhookUrl(request.getWebhookUrl());

        NotificationConfig config = NotificationConfig.builder()
                .organization(org)
                .channel(channel)
                .webhookUrl(request.getWebhookUrl())
                .channelName(request.getChannelName())
                .build();

        applyOptionalFields(config, request);
        config = notificationConfigRepository.save(config);

        log.info("Notification config created for org={}, channel={}", orgId, channel);
        return toResponse(config);
    }

    @Transactional
    public NotificationConfigResponse update(Long configId, NotificationConfigRequest request) {
        NotificationConfig config = notificationConfigRepository.findById(configId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification config not found: " + configId));

        if (request.getWebhookUrl() != null) {
            validateWebhookUrl(request.getWebhookUrl());
            config.setWebhookUrl(request.getWebhookUrl());
        }
        if (request.getChannelName() != null) config.setChannelName(request.getChannelName());
        applyOptionalFields(config, request);

        config = notificationConfigRepository.save(config);
        log.info("Notification config updated id={}", configId);
        return toResponse(config);
    }

    @Transactional
    public void delete(Long configId) {
        if (!notificationConfigRepository.existsById(configId)) {
            throw new ResourceNotFoundException("Notification config not found: " + configId);
        }
        notificationConfigRepository.deleteById(configId);
        log.info("Notification config deleted id={}", configId);
    }

    // ── Send Notifications ──────────────────────────────────────

    @Async
    public void notifyTaskCompleted(Long orgId, String taskTitle, String projectName) {
        String message = "Task completed: **%s** in project *%s*".formatted(taskTitle, projectName);
        sendToAll(orgId, "notify_task_completed", message);
    }

    @Async
    public void notifyTaskFailed(Long orgId, String taskTitle, String error) {
        String message = "Task failed: **%s** — %s".formatted(taskTitle, error);
        sendToAll(orgId, "notify_task_failed", message);
    }

    @Async
    public void notifyApprovalRequired(Long orgId, String taskTitle, String approverName) {
        String message = "Approval required: **%s** is waiting for review by *%s*".formatted(taskTitle, approverName);
        sendToAll(orgId, "notify_approval_required", message);
    }

    @Async
    public void notifyAgentError(Long orgId, String agentName, String error) {
        String message = "Agent error: **%s** — %s".formatted(agentName, error);
        sendToAll(orgId, "notify_agent_error", message);
    }

    // ── Internal ────────────────────────────────────────────────

    private void sendToAll(Long orgId, String eventField, String message) {
        List<NotificationConfig> configs = notificationConfigRepository
                .findByOrganizationIdAndEnabledTrue(orgId);

        for (NotificationConfig config : configs) {
            if (!isEventEnabled(config, eventField)) continue;

            try {
                sendWebhook(config, message);
            } catch (Exception e) {
                log.error("Failed to send {} notification to {} for org={}: {}",
                        config.getChannel(), config.getWebhookUrl(), orgId, e.getMessage());
            }
        }
    }

    private void sendWebhook(NotificationConfig config, String message) {
        var provider = providerRegistry.getProvider(config.getChannel());
        provider.send(message, config.getWebhookUrl());
        log.debug("Notification sent via {} to org={}", config.getChannel(), config.getOrganization().getId());
    }

    private boolean isEventEnabled(NotificationConfig config, String eventField) {
        return switch (eventField) {
            case "notify_task_completed" -> config.isNotifyTaskCompleted();
            case "notify_task_failed" -> config.isNotifyTaskFailed();
            case "notify_approval_required" -> config.isNotifyApprovalRequired();
            case "notify_agent_error" -> config.isNotifyAgentError();
            default -> false;
        };
    }

    private NotificationChannel parseChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            throw new BadRequestException("Channel is required");
        }
        try {
            return NotificationChannel.valueOf(channel.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid channel: " + channel + ". Supported: SLACK, DISCORD, TEAMS");
        }
    }

    private void validateWebhookUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new BadRequestException("Webhook URL is required");
        }
        if (!url.startsWith("https://")) {
            throw new BadRequestException("Webhook URL must use HTTPS");
        }
    }

    private void applyOptionalFields(NotificationConfig config, NotificationConfigRequest request) {
        if (request.getNotifyTaskCompleted() != null) config.setNotifyTaskCompleted(request.getNotifyTaskCompleted());
        if (request.getNotifyTaskFailed() != null) config.setNotifyTaskFailed(request.getNotifyTaskFailed());
        if (request.getNotifyApprovalRequired() != null) config.setNotifyApprovalRequired(request.getNotifyApprovalRequired());
        if (request.getNotifyAgentError() != null) config.setNotifyAgentError(request.getNotifyAgentError());
        if (request.getEnabled() != null) config.setEnabled(request.getEnabled());
    }

    private NotificationConfigResponse toResponse(NotificationConfig config) {
        return NotificationConfigResponse.builder()
                .id(config.getId())
                .organizationId(config.getOrganization().getId())
                .channel(config.getChannel().name())
                .webhookUrl(config.getWebhookUrl())
                .channelName(config.getChannelName())
                .notifyTaskCompleted(config.isNotifyTaskCompleted())
                .notifyTaskFailed(config.isNotifyTaskFailed())
                .notifyApprovalRequired(config.isNotifyApprovalRequired())
                .notifyAgentError(config.isNotifyAgentError())
                .enabled(config.isEnabled())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
