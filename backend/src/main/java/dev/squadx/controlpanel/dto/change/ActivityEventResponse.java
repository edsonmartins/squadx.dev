package dev.squadx.controlpanel.dto.change;

import dev.squadx.controlpanel.model.SpecEvent;
import dev.squadx.controlpanel.model.enums.EventSource;
import dev.squadx.controlpanel.model.enums.TaskEventType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.Instant;

/**
 * Item do feed de atividade de um projeto (dashboard "onde estamos").
 * Projeta o evento com contexto mínimo da tarefa para exibição sem N+1 na UI.
 */
@Builder
public record ActivityEventResponse(
        Long id,
        @JsonProperty("spec_task_id") Long specTaskId,
        @JsonProperty("task_title") String taskTitle,
        TaskEventType type,
        EventSource source,
        @JsonProperty("source_ref") String sourceRef,
        @JsonProperty("actor_name") String actorName,
        @JsonProperty("occurred_at") Instant occurredAt
) {

    public static ActivityEventResponse from(SpecEvent event) {
        return ActivityEventResponse.builder()
                .id(event.getId())
                .specTaskId(event.getSpecTask() != null ? event.getSpecTask().getId() : null)
                .taskTitle(event.getSpecTask() != null ? event.getSpecTask().getTitle() : null)
                .type(event.getType())
                .source(event.getSource())
                .sourceRef(event.getSourceRef())
                .actorName(event.getActor() != null ? event.getActor().getFullName() : null)
                .occurredAt(event.getOccurredAt())
                .build();
    }
}
