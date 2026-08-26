package dev.squadx.observability;

import dev.squadx.event.AgentDeadEvent;
import dev.squadx.event.ExecutionCompletedEvent;
import dev.squadx.event.ExecutionStartedEvent;
import dev.squadx.model.Agent;
import dev.squadx.model.Execution;
import dev.squadx.model.enums.ExecutionStatus;
import dev.squadx.repository.AgentRepository;
import dev.squadx.repository.ExecutionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Product-level telemetry for SquadX.
 *
 * <p>Only bounded dimensions are labels. Organization, user, task, execution and agent IDs are
 * intentionally excluded: putting tenant identifiers in Prometheus labels creates unbounded
 * cardinality and leaks business context into the monitoring plane.
 */
@Component
@Slf4j
public class BusinessMetrics {

    private static final List<String> AGENT_STATES = List.of("IDLE", "WORKING", "DEAD", "PAUSED");

    private final MeterRegistry registry;
    private final ExecutionRepository executionRepository;
    private final AgentRepository agentRepository;
    private final Map<ExecutionStatus, AtomicLong> executionsByStatus = new EnumMap<>(ExecutionStatus.class);
    private final Map<String, AtomicLong> agentsByState;

    public BusinessMetrics(MeterRegistry registry, ExecutionRepository executionRepository,
                           AgentRepository agentRepository) {
        this.registry = registry;
        this.executionRepository = executionRepository;
        this.agentRepository = agentRepository;

        for (ExecutionStatus status : ExecutionStatus.values()) {
            AtomicLong value = new AtomicLong();
            executionsByStatus.put(status, value);
            Gauge.builder("squadx.business.executions.active", value, AtomicLong::get)
                    .description("Current executions grouped by lifecycle status")
                    .tag("status", label(status.name()))
                    .register(registry);
        }

        Map<String, AtomicLong> states = new java.util.LinkedHashMap<>();
        for (String state : AGENT_STATES) {
            AtomicLong value = new AtomicLong();
            states.put(state, value);
            Gauge.builder("squadx.business.agents", value, AtomicLong::get)
                    .description("Current agents grouped by lifecycle state")
                    .tag("state", label(state))
                    .register(registry);
        }
        this.agentsByState = Map.copyOf(states);
    }

    @EventListener
    public void onExecutionStarted(ExecutionStartedEvent event) {
        executionRepository.findById(event.executionId()).ifPresent(execution ->
                Counter.builder("squadx.business.executions.started")
                        .description("Executions admitted by SquadX")
                        .tags(classificationTags(execution))
                        .register(registry)
                        .increment());
    }

    @EventListener
    public void onExecutionCompleted(ExecutionCompletedEvent event) {
        executionRepository.findById(event.executionId()).ifPresent(execution -> {
            String[] dimensions = classificationTags(execution);
            String outcome = label(event.status().name());

            Counter.builder("squadx.business.executions.completed")
                    .description("Terminal executions grouped by outcome")
                    .tags(dimensions)
                    .tag("outcome", outcome)
                    .register(registry)
                    .increment();

            if (execution.getStartedAt() != null && execution.getCompletedAt() != null) {
                long durationNanos = Duration.between(execution.getStartedAt(), execution.getCompletedAt()).toNanos();
                Timer.builder("squadx.business.execution.duration")
                        .description("End-to-end execution duration")
                        .publishPercentileHistogram()
                        .tags(dimensions)
                        .tag("outcome", outcome)
                        .register(registry)
                        .record(Math.max(0, durationNanos), TimeUnit.NANOSECONDS);
            }

            incrementUsage("input", execution.getInputTokens(), dimensions);
            incrementUsage("output", execution.getOutputTokens(), dimensions);
            if (execution.getTotalCost() != null && execution.getTotalCost() > 0) {
                Counter.builder("squadx.business.execution.cost.usd")
                        .description("Reported execution cost in US dollars")
                        .tags(dimensions)
                        .register(registry)
                        .increment(execution.getTotalCost());
            }
        });
    }

    @EventListener
    public void onAgentDead(AgentDeadEvent event) {
        agentRepository.findById(event.agentId()).ifPresent(agent ->
                Counter.builder("squadx.business.agents.dead")
                        .description("Agents declared dead by the heartbeat watchdog")
                        .tag("runtime", runtime(agent))
                        .tag("agent_type", agent.getAgentType() != null ? label(agent.getAgentType().name()) : "unknown")
                        .register(registry)
                        .increment());
    }

    /** Refresh database-backed snapshots outside the Prometheus scrape request. */
    @Scheduled(fixedDelayString = "${squadx.metrics.snapshot-interval-ms:30000}")
    public void refreshSnapshots() {
        try {
            executionsByStatus.forEach((status, value) -> value.set(executionRepository.countByStatus(status)));
            agentsByState.forEach((state, value) -> value.set(agentRepository.countByLifecycleState(state)));
        } catch (RuntimeException error) {
            log.warn("Unable to refresh business metric snapshots: {}", error.getMessage());
        }
    }

    private void incrementUsage(String direction, Long tokens, String[] dimensions) {
        if (tokens == null || tokens <= 0) {
            return;
        }
        Counter.builder("squadx.business.execution.tokens")
                .description("LLM tokens consumed by completed executions")
                .tags(dimensions)
                .tag("direction", direction)
                .register(registry)
                .increment(tokens);
    }

    private String[] classificationTags(Execution execution) {
        Agent agent = execution.getAgent();
        return new String[] {
                "runtime", runtime(agent),
                "agent_type", agent != null && agent.getAgentType() != null
                        ? label(agent.getAgentType().name()) : "unknown",
                "provider", agent != null && agent.getCliProvider() != null
                        ? label(agent.getCliProvider().name()) : "unknown"
        };
    }

    private String runtime(Agent agent) {
        return agent != null && agent.getRuntimeKind() != null
                ? label(agent.getRuntimeKind().name()) : "native";
    }

    private static String label(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
