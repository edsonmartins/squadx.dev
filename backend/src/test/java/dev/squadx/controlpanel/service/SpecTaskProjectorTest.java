package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.model.SpecEvent;
import dev.squadx.controlpanel.model.enums.Pass5Result;
import dev.squadx.controlpanel.model.enums.SpecTaskStatus;
import dev.squadx.controlpanel.model.enums.TaskEventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpecTaskProjectorTest {

    private final SpecTaskProjector projector = new SpecTaskProjector(new SpecTaskStateMachine());

    private SpecEvent ev(TaskEventType type, String payload) {
        return SpecEvent.builder().type(type).payload(payload).occurredAt(Instant.now()).build();
    }

    @Test
    void happyPathConcludes() {
        var state = projector.project(List.of(
                ev(TaskEventType.STARTED, null),
                ev(TaskEventType.PR_OPENED, null),
                ev(TaskEventType.PASS5_APPROVED, null)));
        assertThat(state.status()).isEqualTo(SpecTaskStatus.CONCLUIDA);
        assertThat(state.pass5()).isEqualTo(Pass5Result.PASS);
    }

    @Test
    void implementedDoesNotConclude() {  // R3
        var state = projector.project(List.of(
                ev(TaskEventType.STARTED, null),
                ev(TaskEventType.IMPLEMENTED, null)));
        assertThat(state.status()).isEqualTo(SpecTaskStatus.EM_CURSO);
    }

    @Test
    void blockThenUnblock() {
        var blocked = projector.project(List.of(
                ev(TaskEventType.STARTED, null),
                ev(TaskEventType.BLOCKED, "waiting on API")));
        assertThat(blocked.status()).isEqualTo(SpecTaskStatus.BLOQUEADA);
        assertThat(blocked.blockerReason()).isEqualTo("waiting on API");

        var unblocked = projector.project(List.of(
                ev(TaskEventType.STARTED, null),
                ev(TaskEventType.BLOCKED, "waiting on API"),
                ev(TaskEventType.UNBLOCKED, null)));
        assertThat(unblocked.status()).isEqualTo(SpecTaskStatus.EM_CURSO);
        assertThat(unblocked.blockerReason()).isNull();
    }

    @Test
    void pass5ChangesReopensWithCritique() {
        var state = projector.project(List.of(
                ev(TaskEventType.STARTED, null),
                ev(TaskEventType.PR_OPENED, null),
                ev(TaskEventType.PASS5_CHANGES, "cenário X sem teste")));
        assertThat(state.status()).isEqualTo(SpecTaskStatus.AJUSTES);
        assertThat(state.pass5()).isEqualTo(Pass5Result.FAIL);
        assertThat(state.reviseReason()).isEqualTo("cenário X sem teste");
    }

    @Test
    void ignoresInvalidTransitions() {
        // PR_OPENED a partir de A_FAZER é inválido — ignorado; estado permanece inicial.
        var state = projector.project(List.of(ev(TaskEventType.PR_OPENED, null)));
        assertThat(state.status()).isEqualTo(SpecTaskStatus.A_FAZER);
    }

    @Test
    void emptyEventsYieldInitialState() {
        var state = projector.project(List.of());
        assertThat(state.status()).isEqualTo(SpecTaskStatus.A_FAZER);
        assertThat(state.pass5()).isEqualTo(Pass5Result.PENDING);
    }
}
