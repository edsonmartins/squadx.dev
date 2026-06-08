package dev.squadx.service;

import dev.squadx.dto.autopilot.AutopilotRequest;
import dev.squadx.dto.autopilot.AutopilotResponse;
import dev.squadx.dto.autopilot.AutopilotRunResponse;
import dev.squadx.dto.common.PageResponse;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.*;
import dev.squadx.model.enums.AutopilotExecutionMode;
import dev.squadx.model.enums.AutopilotTriggerType;
import dev.squadx.model.enums.TaskPriority;
import dev.squadx.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutopilotService {

    private final AutopilotRepository autopilotRepository;
    private final AutopilotRunRepository autopilotRunRepository;
    private final ProjectRepository projectRepository;
    private final SquadRepository squadRepository;
    private final AgentRepository agentRepository;
    private final OrganizationMemberRepository memberRepository;
    private final JobScheduler jobScheduler;
    private final AutopilotExecutor autopilotExecutor;

    // ---- CRUD ----

    @Transactional
    public AutopilotResponse create(AutopilotRequest request, User currentUser) {
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        Long organizationId = project.getOrganization().getId();
        validateUserAccess(organizationId, currentUser.getId());

        Autopilot autopilot = Autopilot.builder()
                .name(request.getName())
                .description(request.getDescription())
                .cronExpression(request.getCronExpression())
                .timezone(request.getTimezone() != null ? request.getTimezone() : "UTC")
                .executionMode(request.getExecutionMode() != null
                        ? request.getExecutionMode() : AutopilotExecutionMode.CREATE_TASK)
                .organization(project.getOrganization())
                .project(project)
                .taskTitle(request.getTaskTitle())
                .taskDescription(request.getTaskDescription())
                .taskPriority(request.getTaskPriority() != null ? request.getTaskPriority() : TaskPriority.MEDIUM)
                .enabled(request.getEnabled() == null || request.getEnabled())
                .createdBy(currentUser)
                .build();

        applyTargets(autopilot, request, organizationId);

        autopilot = autopilotRepository.save(autopilot);
        syncSchedule(autopilot);

        return mapToResponse(autopilot);
    }

    @Transactional(readOnly = true)
    public AutopilotResponse getById(Long id, User currentUser) {
        Autopilot autopilot = loadForUser(id, currentUser);
        return mapToResponse(autopilot);
    }

    @Transactional(readOnly = true)
    public PageResponse<AutopilotResponse> getByOrganizationId(Long organizationId, Pageable pageable, User currentUser) {
        validateUserAccess(organizationId, currentUser.getId());
        Page<AutopilotResponse> page = autopilotRepository.findByOrganizationId(organizationId, pageable)
                .map(this::mapToResponse);
        return PageResponse.from(page);
    }

    @Transactional
    public AutopilotResponse update(Long id, AutopilotRequest request, User currentUser) {
        Autopilot autopilot = loadForUser(id, currentUser);
        Long organizationId = autopilot.getOrganization().getId();

        if (request.getName() != null) {
            autopilot.setName(request.getName());
        }
        if (request.getDescription() != null) {
            autopilot.setDescription(request.getDescription());
        }
        if (request.getCronExpression() != null) {
            autopilot.setCronExpression(request.getCronExpression());
        }
        if (request.getTimezone() != null) {
            autopilot.setTimezone(request.getTimezone());
        }
        if (request.getExecutionMode() != null) {
            autopilot.setExecutionMode(request.getExecutionMode());
        }
        if (request.getTaskTitle() != null) {
            autopilot.setTaskTitle(request.getTaskTitle());
        }
        if (request.getTaskDescription() != null) {
            autopilot.setTaskDescription(request.getTaskDescription());
        }
        if (request.getTaskPriority() != null) {
            autopilot.setTaskPriority(request.getTaskPriority());
        }
        if (request.getEnabled() != null) {
            autopilot.setEnabled(request.getEnabled());
        }
        applyTargets(autopilot, request, organizationId);

        autopilot = autopilotRepository.save(autopilot);
        syncSchedule(autopilot);

        return mapToResponse(autopilot);
    }

    @Transactional
    public AutopilotResponse toggle(Long id, User currentUser) {
        Autopilot autopilot = loadForUser(id, currentUser);
        autopilot.setEnabled(!autopilot.isEnabled());
        autopilot = autopilotRepository.save(autopilot);
        syncSchedule(autopilot);
        return mapToResponse(autopilot);
    }

    @Transactional
    public void delete(Long id, User currentUser) {
        Autopilot autopilot = loadForUser(id, currentUser);
        unschedule(autopilot.getId());
        autopilotRepository.delete(autopilot);
    }

    @Transactional(readOnly = true)
    public PageResponse<AutopilotRunResponse> getRuns(Long id, Pageable pageable, User currentUser) {
        loadForUser(id, currentUser);
        Page<AutopilotRunResponse> page = autopilotRunRepository
                .findByAutopilotIdOrderByTriggeredAtDesc(id, pageable)
                .map(this::mapRunToResponse);
        return PageResponse.from(page);
    }

    /** Manually fire an autopilot now (does not wait for the schedule). */
    public AutopilotRunResponse runNow(Long id, User currentUser) {
        loadForUser(id, currentUser);
        AutopilotRun run = autopilotExecutor.execute(id, AutopilotTriggerType.MANUAL);
        return run != null ? mapRunToResponse(run) : null;
    }

    // ---- Scheduling ----

    /** Re-register all enabled autopilots with JobRunr on startup (idempotent). */
    @EventListener(ApplicationReadyEvent.class)
    public void reconcileSchedulesOnStartup() {
        List<Autopilot> enabled = autopilotRepository.findByEnabledTrue();
        log.info("Reconciling {} enabled autopilot schedule(s) with JobRunr", enabled.size());
        for (Autopilot autopilot : enabled) {
            try {
                scheduleJob(autopilot);
            } catch (Exception e) {
                log.error("Failed to reconcile schedule for autopilot {}: {}", autopilot.getId(), e.getMessage());
            }
        }
    }

    private void syncSchedule(Autopilot autopilot) {
        if (autopilot.isEnabled()) {
            scheduleJob(autopilot);
        } else {
            unschedule(autopilot.getId());
        }
    }

    private void scheduleJob(Autopilot autopilot) {
        Long autopilotId = autopilot.getId();
        JobLambda job = () -> autopilotExecutor.runScheduled(autopilotId);
        jobScheduler.scheduleRecurrently(jobId(autopilotId), autopilot.getCronExpression(),
                resolveZone(autopilot.getTimezone()), job);
        log.info("Scheduled autopilot {} with cron '{}' ({})",
                autopilotId, autopilot.getCronExpression(), autopilot.getTimezone());
    }

    private void unschedule(Long autopilotId) {
        try {
            jobScheduler.deleteRecurringJob(jobId(autopilotId));
        } catch (Exception e) {
            log.debug("No recurring job to delete for autopilot {}: {}", autopilotId, e.getMessage());
        }
    }

    private String jobId(Long autopilotId) {
        return "autopilot-" + autopilotId;
    }

    private ZoneId resolveZone(String timezone) {
        try {
            return timezone != null ? ZoneId.of(timezone) : ZoneId.of("UTC");
        } catch (Exception e) {
            log.warn("Invalid timezone '{}', defaulting to UTC", timezone);
            return ZoneId.of("UTC");
        }
    }

    // ---- Helpers ----

    private void applyTargets(Autopilot autopilot, AutopilotRequest request, Long organizationId) {
        if (request.getTargetAgentId() != null) {
            Agent agent = agentRepository.findById(request.getTargetAgentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Agent not found"));
            if (!agent.getSquad().getOrganization().getId().equals(organizationId)) {
                throw new BadRequestException("Agent does not belong to this organization");
            }
            autopilot.setTargetAgent(agent);
        } else {
            autopilot.setTargetAgent(null);
        }

        if (request.getTargetSquadId() != null) {
            Squad squad = squadRepository.findById(request.getTargetSquadId())
                    .orElseThrow(() -> new ResourceNotFoundException("Squad not found"));
            if (!squad.getOrganization().getId().equals(organizationId)) {
                throw new BadRequestException("Squad does not belong to this organization");
            }
            autopilot.setTargetSquad(squad);
        } else {
            autopilot.setTargetSquad(null);
        }
    }

    private Autopilot loadForUser(Long id, User currentUser) {
        Autopilot autopilot = autopilotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Autopilot not found"));
        validateUserAccess(autopilot.getOrganization().getId(), currentUser.getId());
        return autopilot;
    }

    private void validateUserAccess(Long organizationId, Long userId) {
        if (!memberRepository.existsByOrganizationIdAndUserId(organizationId, userId)) {
            throw new ForbiddenException("User does not have access to this organization");
        }
    }

    private AutopilotResponse mapToResponse(Autopilot autopilot) {
        return AutopilotResponse.builder()
                .id(autopilot.getId())
                .name(autopilot.getName())
                .description(autopilot.getDescription())
                .cronExpression(autopilot.getCronExpression())
                .timezone(autopilot.getTimezone())
                .executionMode(autopilot.getExecutionMode())
                .organizationId(autopilot.getOrganization().getId())
                .projectId(autopilot.getProject().getId())
                .projectName(autopilot.getProject().getName())
                .targetSquadId(autopilot.getTargetSquad() != null ? autopilot.getTargetSquad().getId() : null)
                .targetSquadName(autopilot.getTargetSquad() != null ? autopilot.getTargetSquad().getName() : null)
                .targetAgentId(autopilot.getTargetAgent() != null ? autopilot.getTargetAgent().getId() : null)
                .targetAgentName(autopilot.getTargetAgent() != null ? autopilot.getTargetAgent().getName() : null)
                .taskTitle(autopilot.getTaskTitle())
                .taskDescription(autopilot.getTaskDescription())
                .taskPriority(autopilot.getTaskPriority())
                .enabled(autopilot.isEnabled())
                .lastRunAt(autopilot.getLastRunAt())
                .nextRunAt(autopilot.getNextRunAt())
                .runCount(autopilot.getRunCount())
                .createdById(autopilot.getCreatedBy() != null ? autopilot.getCreatedBy().getId() : null)
                .createdByName(autopilot.getCreatedBy() != null ? autopilot.getCreatedBy().getFullName() : null)
                .createdAt(autopilot.getCreatedAt())
                .updatedAt(autopilot.getUpdatedAt())
                .build();
    }

    private AutopilotRunResponse mapRunToResponse(AutopilotRun run) {
        return AutopilotRunResponse.builder()
                .id(run.getId())
                .autopilotId(run.getAutopilot().getId())
                .triggerType(run.getTriggerType())
                .status(run.getStatus())
                .createdTaskId(run.getCreatedTaskId())
                .executionId(run.getExecutionId())
                .message(run.getMessage())
                .triggeredAt(run.getTriggeredAt())
                .build();
    }
}
