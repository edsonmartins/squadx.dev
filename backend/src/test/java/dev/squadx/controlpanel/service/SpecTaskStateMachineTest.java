package dev.squadx.controlpanel.service;

import org.junit.jupiter.api.Test;

import static dev.squadx.controlpanel.model.enums.SpecTaskStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class SpecTaskStateMachineTest {

    private final SpecTaskStateMachine sm = new SpecTaskStateMachine();

    @Test
    void allowsTheHappyPath() {
        assertThat(sm.canTransition(A_FAZER, EM_CURSO)).isTrue();
        assertThat(sm.canTransition(EM_CURSO, EM_VALIDACAO)).isTrue();
        assertThat(sm.canTransition(EM_VALIDACAO, CONCLUIDA)).isTrue();
    }

    @Test
    void allowsReopenFromAjustes() {
        assertThat(sm.canTransition(AJUSTES, EM_CURSO)).isTrue();
    }

    @Test
    void allowsBlockAndUnblock() {
        assertThat(sm.canTransition(EM_CURSO, BLOQUEADA)).isTrue();
        assertThat(sm.canTransition(EM_VALIDACAO, BLOQUEADA)).isTrue();
        assertThat(sm.canTransition(BLOQUEADA, EM_CURSO)).isTrue();
    }

    @Test
    void rejectsInvalidTransitions() {
        assertThat(sm.canTransition(A_FAZER, CONCLUIDA)).isFalse();
        assertThat(sm.canTransition(A_FAZER, EM_VALIDACAO)).isFalse();
        assertThat(sm.canTransition(EM_CURSO, CONCLUIDA)).isFalse();
    }

    @Test
    void concluidaIsTerminal() {
        assertThat(sm.allowedTargets(CONCLUIDA)).isEmpty();
    }

    @Test
    void marksPass5OnlyTargets() {
        assertThat(sm.isPass5Only(CONCLUIDA)).isTrue();
        assertThat(sm.isPass5Only(AJUSTES)).isTrue();
        assertThat(sm.isPass5Only(EM_CURSO)).isFalse();
        assertThat(sm.isPass5Only(EM_VALIDACAO)).isFalse();
    }
}
