package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.dto.change.ActivityEventResponse;
import dev.squadx.controlpanel.model.Change;
import dev.squadx.controlpanel.model.SpecEvent;
import dev.squadx.controlpanel.model.SpecTask;
import dev.squadx.controlpanel.model.enums.EventSource;
import dev.squadx.controlpanel.model.enums.TaskEventType;
import dev.squadx.controlpanel.repository.SpecEventRepository;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Organization;
import dev.squadx.model.Project;
import dev.squadx.model.User;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpecEventServiceRecentActivityTest {

    @Mock private SpecEventRepository specEventRepository;
    @Mock private dev.squadx.controlpanel.repository.SpecTaskRepository specTaskRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private OrganizationMemberRepository memberRepository;
    @Mock private SpecTaskProjector projector;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @InjectMocks private SpecEventService service;

    private User user(long id) {
        User u = new User();
        u.setId(id);
        u.setFullName("Edson");
        return u;
    }

    private SpecEvent event(long id, String taskTitle, EventSource source) {
        SpecTask task = SpecTask.builder().title(taskTitle).build();
        task.setId(9L);
        SpecEvent specEvent = SpecEvent.builder()
                .specTask(task)
                .actor(user(1L))
                .type(TaskEventType.IMPLEMENTED)
                .source(source)
                .occurredAt(Instant.parse("2026-08-26T10:00:00Z"))
                .build();
        specEvent.setId(id);
        return specEvent;
    }

    @Test
    void mapsEventsWithContextAndCapsLimit() {
        Project project = new Project();
        Organization org = new Organization();
        org.setId(5L);
        project.setOrganization(org);
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(memberRepository.existsByOrganizationIdAndUserId(5L, 1L)).thenReturn(true);
        when(specEventRepository.findRecentByProject(eq(7L), any(PageRequest.class))).thenReturn(
                List.of(event(2L, "Rodar migração", EventSource.MCP),
                        event(1L, "Criar endpoint", EventSource.GIT)));

        List<ActivityEventResponse> feed = service.recentForProject(7L, user(1L), 500);

        assertThat(feed).hasSize(2);
        assertThat(feed.get(0).taskTitle()).isEqualTo("Rodar migração");
        assertThat(feed.get(0).source()).isEqualTo(EventSource.MCP);
        assertThat(feed.get(0).actorName()).isEqualTo("Edson");
        verify(specEventRepository).findRecentByProject(eq(7L), eq(PageRequest.of(0, 100)));
    }

    @Test
    void forbiddenWhenUserIsNotOrganizationMember() {
        Project project = new Project();
        Organization org = new Organization();
        org.setId(5L);
        project.setOrganization(org);
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(memberRepository.existsByOrganizationIdAndUserId(5L, 99L)).thenReturn(false);

        assertThatThrownBy(() -> service.recentForProject(7L, user(99L), 30))
                .isInstanceOf(ForbiddenException.class);
        verify(specEventRepository, never()).findRecentByProject(any(), any());
    }

    @Test
    void projectMustExist() {
        when(projectRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recentForProject(404L, user(1L), 30))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
