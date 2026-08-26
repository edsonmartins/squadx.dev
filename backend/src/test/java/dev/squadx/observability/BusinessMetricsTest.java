package dev.squadx.observability;

import dev.squadx.event.ExecutionCompletedEvent;
import dev.squadx.model.Agent;
import dev.squadx.model.Execution;
import dev.squadx.model.enums.AgentRuntimeKind;
import dev.squadx.model.enums.AgentType;
import dev.squadx.model.enums.ExecutionStatus;
import dev.squadx.repository.AgentRepository;
import dev.squadx.repository.ExecutionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessMetricsTest {

    @Test
    void recordsBoundedExecutionDimensionsAndUsage() {
        var registry = new SimpleMeterRegistry();
        var executions = mock(ExecutionRepository.class);
        var agents = mock(AgentRepository.class);
        var metrics = new BusinessMetrics(registry, executions, agents);
        var agent = Agent.builder()
                .agentType(AgentType.BACKEND)
                .runtimeKind(AgentRuntimeKind.NATIVE)
                .build();
        var execution = Execution.builder()
                .agent(agent)
                .status(ExecutionStatus.COMPLETED)
                .startedAt(Instant.parse("2026-08-05T10:00:00Z"))
                .completedAt(Instant.parse("2026-08-05T10:02:00Z"))
                .inputTokens(100L)
                .outputTokens(25L)
                .totalCost(0.50)
                .build();
        execution.setId(7L);
        when(executions.findById(7L)).thenReturn(Optional.of(execution));

        metrics.onExecutionCompleted(new ExecutionCompletedEvent(7L, 1L, 2L, 3L,
                ExecutionStatus.COMPLETED));

        assertThat(registry.get("squadx.business.executions.completed")
                .tag("outcome", "completed").counter().count()).isEqualTo(1);
        assertThat(registry.get("squadx.business.execution.duration")
                .tag("outcome", "completed").timer().totalTime(java.util.concurrent.TimeUnit.SECONDS))
                .isEqualTo(120);
        assertThat(registry.get("squadx.business.execution.tokens")
                .tag("direction", "input").counter().count()).isEqualTo(100);
        assertThat(registry.get("squadx.business.execution.cost.usd").counter().count()).isEqualTo(0.50);
    }

    @Test
    void refreshesStatusSnapshotsOutsideTheScrapePath() {
        var registry = new SimpleMeterRegistry();
        var executions = mock(ExecutionRepository.class);
        var agents = mock(AgentRepository.class);
        when(executions.countByStatus(ExecutionStatus.RUNNING)).thenReturn(4L);
        when(agents.countByLifecycleState("IDLE")).thenReturn(3L);
        var metrics = new BusinessMetrics(registry, executions, agents);

        metrics.refreshSnapshots();

        assertThat(registry.get("squadx.business.executions.active")
                .tag("status", "running").gauge().value()).isEqualTo(4);
        assertThat(registry.get("squadx.business.agents")
                .tag("state", "idle").gauge().value()).isEqualTo(3);
    }
}
