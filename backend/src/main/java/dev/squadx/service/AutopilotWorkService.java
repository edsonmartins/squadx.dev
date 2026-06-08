package dev.squadx.service;

import dev.squadx.dto.execution.ExecutionRequest;
import dev.squadx.dto.execution.ExecutionResponse;
import dev.squadx.dto.task.TaskRequest;
import dev.squadx.dto.task.TaskResponse;
import dev.squadx.model.Agent;
import dev.squadx.model.Autopilot;
import dev.squadx.model.AutopilotRun;
import dev.squadx.model.User;
import dev.squadx.model.enums.AutopilotExecutionMode;
import dev.squadx.model.enums.AutopilotRunStatus;
import dev.squadx.model.enums.AutopilotTriggerType;
import dev.squadx.repository.AutopilotRepository;
import dev.squadx.repository.AutopilotRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Transactional units of work for autopilot firing, kept separate from the
 * (non-transactional) orchestration in {@link dev.squadx.service.AutopilotExecutor}.
 *
 * <p>{@link #perform} runs in the default (REQUIRED) transaction: if anything throws,
 * the created task/execution roll back as a unit. {@link #record} runs in its own
 * REQUIRES_NEW transaction so the run history (especially FAILED runs) is persisted
 * even when {@link #perform} rolled back.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutopilotWorkService {

    private final AutopilotRepository autopilotRepository;
    private final AutopilotRunRepository autopilotRunRepository;
    private final SquadAgentResolver squadAgentResolver;
    private final TaskService taskService;
    private final ExecutionService executionService;

    /** Outcome of a single firing, carried back to the orchestrator for recording. */
    public record PerformResult(AutopilotRunStatus status, Long taskId, Long executionId, String message) {
        static PerformResult success(Long taskId, Long executionId, String message) {
            return new PerformResult(AutopilotRunStatus.SUCCESS, taskId, executionId, message);
        }

        static PerformResult skipped(String message) {
            return new PerformResult(AutopilotRunStatus.SKIPPED, null, null, message);
        }

        static PerformResult failed(String message) {
            return new PerformResult(AutopilotRunStatus.FAILED, null, null, message);
        }
    }

    @Transactional
    public PerformResult perform(Long autopilotId, AutopilotTriggerType trigger) {
        Autopilot autopilot = autopilotRepository.findById(autopilotId).orElse(null);
        if (autopilot == null) {
            return PerformResult.skipped("Autopilot no longer exists");
        }
        if (!autopilot.isEnabled() && trigger != AutopilotTriggerType.MANUAL) {
            return PerformResult.skipped("Autopilot is disabled");
        }

        User owner = autopilot.getCreatedBy();
        if (owner == null) {
            return PerformResult.failed("Autopilot has no owner user to attribute work to");
        }

        Agent agent = autopilot.getTargetAgent() != null
                ? autopilot.getTargetAgent()
                : squadAgentResolver.resolve(autopilot.getTargetSquad());

        boolean runTask = autopilot.getExecutionMode() == AutopilotExecutionMode.RUN_TASK;
        if (runTask) {
            if (agent == null) {
                return PerformResult.skipped("No target agent available for RUN_TASK mode");
            }
            if (!squadAgentResolver.isOnline(agent)) {
                return PerformResult.skipped("Target agent '" + agent.getName() + "' is offline");
            }
        }

        TaskRequest taskRequest = TaskRequest.builder()
                .title(autopilot.getTaskTitle())
                .description(autopilot.getTaskDescription())
                .priority(autopilot.getTaskPriority())
                .projectId(autopilot.getProject().getId())
                .assignedAgentId(agent != null ? agent.getId() : null)
                .assignedSquadId(autopilot.getTargetSquad() != null
                        ? autopilot.getTargetSquad().getId() : null)
                .build();

        TaskResponse task = taskService.create(taskRequest, owner);

        Long executionId = null;
        if (runTask) {
            ExecutionResponse execution = executionService.startExecution(
                    ExecutionRequest.builder()
                            .taskId(task.getId())
                            .agentId(agent.getId())
                            .build(),
                    owner);
            executionId = execution.getId();
            return PerformResult.success(task.getId(), executionId,
                    "Created task #" + task.getId() + " and started execution #" + executionId);
        }

        return PerformResult.success(task.getId(), null, "Created task #" + task.getId());
    }

    /**
     * Persists the run history and bumps the autopilot counters. REQUIRES_NEW so it
     * commits independently of {@link #perform}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AutopilotRun record(Long autopilotId, AutopilotTriggerType trigger, PerformResult result) {
        Autopilot autopilot = autopilotRepository.findById(autopilotId).orElse(null);
        if (autopilot == null) {
            log.warn("Skipping autopilot run record: autopilot {} no longer exists", autopilotId);
            return null;
        }

        AutopilotRun run = AutopilotRun.builder()
                .autopilot(autopilot)
                .triggerType(trigger)
                .status(result.status())
                .createdTaskId(result.taskId())
                .executionId(result.executionId())
                .message(result.message())
                .triggeredAt(Instant.now())
                .build();
        run = autopilotRunRepository.save(run);

        autopilot.setLastRunAt(Instant.now());
        autopilot.setRunCount((autopilot.getRunCount() == null ? 0 : autopilot.getRunCount()) + 1);
        autopilotRepository.save(autopilot);

        return run;
    }
}
