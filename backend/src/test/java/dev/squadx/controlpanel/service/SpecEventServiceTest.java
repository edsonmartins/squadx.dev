package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.event.SpecTaskProjectedEvent;
import dev.squadx.controlpanel.model.Change;
import dev.squadx.controlpanel.model.SpecEvent;
import dev.squadx.controlpanel.model.SpecTask;
import dev.squadx.controlpanel.model.enums.EventSource;
import dev.squadx.controlpanel.model.enums.SpecTaskStatus;
import dev.squadx.controlpanel.model.enums.TaskEventType;
import dev.squadx.controlpanel.repository.SpecEventRepository;
import dev.squadx.controlpanel.repository.SpecTaskRepository;
import dev.squadx.model.Organization;
import dev.squadx.model.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpecEventServiceTest {

    @Mock private SpecEventRepository specEventRepository;
    @Mock private SpecTaskRepository specTaskRepository;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Spy private SpecTaskProjector projector = new SpecTaskProjector(new SpecTaskStateMachine());

    @InjectMocks private SpecEventService service;

    private SpecTask task;

    @BeforeEach
    void setUp() {
        Organization org = Organization.builder().name("Org").slug("org").build();
        org.setId(100L);
        Project project = Project.builder().name("Proj").slug("proj").organization(org).build();
        project.setId(7L);
        Change change = Change.builder().project(project).build();
        change.setId(5L);
        task = SpecTask.builder().change(change).title("T").status(SpecTaskStatus.A_FAZER).build();
        task.setId(42L);
    }

    @Test
    void duplicateEventIsIgnored() {  // R4
        when(specEventRepository.existsByDedupKey(anyString())).thenReturn(true);

        service.record(42L, TaskEventType.STARTED, EventSource.GIT, "sha-1", null, Instant.now());

        verify(specEventRepository, never()).save(any());
        verify(specTaskRepository, never()).findById(anyLong());
    }

    @Test
    void recordsAndReprojectsStatus() {  // R3
        when(specEventRepository.existsByDedupKey(anyString())).thenReturn(false);
        when(specTaskRepository.findById(42L)).thenReturn(Optional.of(task));
        when(specEventRepository.findBySpecTaskIdOrderByOccurredAtAscIdAsc(42L)).thenReturn(List.of(
                SpecEvent.builder().type(TaskEventType.STARTED).occurredAt(Instant.now()).build()));

        service.record(42L, TaskEventType.STARTED, EventSource.MCP, "ref-1", null, Instant.now());

        verify(specEventRepository).save(any(SpecEvent.class));
        assertThat(task.getStatus()).isEqualTo(SpecTaskStatus.EM_CURSO);
        verify(specTaskRepository).save(task);
        verify(eventPublisher).publishEvent(any(SpecTaskProjectedEvent.class));
    }
}
