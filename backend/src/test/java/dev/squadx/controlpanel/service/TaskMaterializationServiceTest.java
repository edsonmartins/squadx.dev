package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.parser.CandidateTask;
import dev.squadx.controlpanel.parser.TaskDecisionParser;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Project;
import dev.squadx.model.Task;
import dev.squadx.model.enums.DecisionSourceKind;
import dev.squadx.repository.ProjectRepository;
import dev.squadx.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskMaterializationServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private TaskDecisionParser parser;
    @InjectMocks private TaskMaterializationService service;

    private Project project(long id) {
        Project p = new Project();
        p.setId(id);
        return p;
    }

    private Task taskWith(long id, String ref) {
        Task t = Task.builder().sourceRef(ref).build();
        t.setId(id);
        return t;
    }

    @Test
    void createsTaskForNewDecision() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project(1L)));
        when(parser.parse(any(), eq("x.md"), eq("RFC"))).thenReturn(List.of(
                new CandidateTask("T-1", "Fazer algo", "P0", "RFC", "x.md#T-1")));
        when(taskRepository.findBySourceRef("x.md#T-1")).thenReturn(Optional.empty());
        when(taskRepository.save(any())).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            t.setId(99L);
            return t;
        });

        List<Long> ids = service.materialize("conteudo", "x.md", "RFC", 1L);

        assertThat(ids).containsExactly(99L);
        verify(taskRepository).save(argThat(t ->
                "Fazer algo".equals(t.getTitle())
                        && t.getSourceRef().equals("x.md#T-1")
                        && t.getSourceKind() == DecisionSourceKind.RFC));
    }

    @Test
    void updatesExistingTaskWithSameAnchor_DoesNotDuplicate() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project(1L)));
        when(parser.parse(any(), eq("x.md"), eq("RFC"))).thenReturn(List.of(
                new CandidateTask("T-1", "Titulo novo", "P1", "RFC", "x.md#T-1")));
        Task existing = taskWith(7L, "x.md#T-1");
        existing.setTitle("Titulo antigo");
        when(taskRepository.findBySourceRef("x.md#T-1")).thenReturn(Optional.of(existing));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Long> ids = service.materialize("c", "x.md", "RFC", 1L);

        // um único registro — upsert, sem duplicar
        assertThat(ids).containsExactly(7L);
        verify(taskRepository, times(1)).save(any());
        assertThat(existing.getTitle()).isEqualTo("Titulo novo");
    }

    @Test
    void returnsEmptyWhenNoTasksParsed() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project(1L)));
        when(parser.parse(any(), eq("x.md"), eq("RFC"))).thenReturn(List.of());

        assertThat(service.materialize("c", "x.md", "RFC", 1L)).isEmpty();
        verify(taskRepository, never()).save(any());
    }

    @Test
    void projectMustExist() {
        when(projectRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.materialize("c", "x.md", "RFC", 999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
