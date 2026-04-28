package dev.squadx.model.vo;

import dev.squadx.model.enums.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskStatusTransitionTest {

    @Nested
    @DisplayName("Valid transitions")
    class ValidTransitions {

        @ParameterizedTest
        @CsvSource({
                "TODO, IN_PROGRESS",
                "TODO, CANCELLED",
                "IN_PROGRESS, IN_REVIEW",
                "IN_PROGRESS, BLOCKED",
                "IN_PROGRESS, DONE",
                "IN_PROGRESS, CANCELLED",
                "IN_REVIEW, DONE",
                "IN_REVIEW, IN_PROGRESS",
                "IN_REVIEW, CANCELLED",
                "BLOCKED, TODO",
                "BLOCKED, IN_PROGRESS",
                "BLOCKED, CANCELLED",
                "DONE, IN_PROGRESS",
                "CANCELLED, TODO"
        })
        @DisplayName("should allow valid transition: {0} -> {1}")
        void shouldAllowValidTransition(TaskStatus from, TaskStatus to) {
            TaskStatusTransition transition = new TaskStatusTransition(from, to);
            assertThat(transition.from()).isEqualTo(from);
            assertThat(transition.to()).isEqualTo(to);
        }
    }

    @Nested
    @DisplayName("Invalid transitions")
    class InvalidTransitions {

        @ParameterizedTest
        @CsvSource({
                "TODO, DONE",
                "TODO, IN_REVIEW",
                "TODO, BLOCKED",
                "DONE, TODO",
                "DONE, CANCELLED",
                "CANCELLED, IN_PROGRESS",
                "CANCELLED, DONE",
                "IN_REVIEW, BLOCKED",
                "BLOCKED, DONE"
        })
        @DisplayName("should reject invalid transition: {0} -> {1}")
        void shouldRejectInvalidTransition(TaskStatus from, TaskStatus to) {
            assertThatThrownBy(() -> new TaskStatusTransition(from, to))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Invalid task status transition")
                    .hasMessageContaining(from.name())
                    .hasMessageContaining(to.name());
        }
    }

    @Nested
    @DisplayName("isValid()")
    class IsValid {

        @Test
        @DisplayName("should return true for valid transition")
        void shouldReturnTrueForValid() {
            assertThat(TaskStatusTransition.isValid(TaskStatus.TODO, TaskStatus.IN_PROGRESS)).isTrue();
        }

        @Test
        @DisplayName("should return false for invalid transition")
        void shouldReturnFalseForInvalid() {
            assertThat(TaskStatusTransition.isValid(TaskStatus.DONE, TaskStatus.TODO)).isFalse();
        }
    }
}
