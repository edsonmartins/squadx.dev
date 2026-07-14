package dev.squadx.websocket;

import dev.squadx.dto.supabase.SupabaseLiveSession;
import dev.squadx.model.Organization;
import dev.squadx.model.Project;
import dev.squadx.model.Task;
import dev.squadx.model.User;
import dev.squadx.repository.ExecutionRepository;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.repository.ProjectRepository;
import dev.squadx.repository.TaskRepository;
import dev.squadx.service.SupabaseLiveSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StompSubscriptionAuthorizerTest {

    @Mock private OrganizationMemberRepository memberRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private ExecutionRepository executionRepository;
    @Mock private SupabaseLiveSessionService liveSessionService;

    @InjectMocks private StompSubscriptionAuthorizer authorizer;

    private User user(long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private void memberOf(long orgId, long userId, boolean member) {
        when(memberRepository.existsByOrganizationIdAndUserId(orgId, userId)).thenReturn(member);
    }

    private Task taskInOrg(long orgId) {
        Organization org = new Organization();
        org.setId(orgId);
        Project project = new Project();
        project.setOrganization(org);
        Task task = new Task();
        task.setProject(project);
        return task;
    }

    @Test
    void allowsMemberOnOrganizationTopic() {
        memberOf(7L, 1L, true);
        assertThatCode(() -> authorizer.authorize("/topic/organizations/7", user(1L)))
                .doesNotThrowAnyException();
    }

    @Test
    void deniesNonMemberOnOrganizationTopic() {
        memberOf(7L, 1L, false);
        assertThatThrownBy(() -> authorizer.authorize("/topic/organizations/7", user(1L)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void resolvesTaskChainAndAllowsMember() {
        when(taskRepository.findById(10L)).thenReturn(Optional.of(taskInOrg(5L)));
        memberOf(5L, 1L, true);
        assertThatCode(() -> authorizer.authorize("/topic/tasks/10", user(1L)))
                .doesNotThrowAnyException();
    }

    @Test
    void deniesTaskForNonMember() {
        when(taskRepository.findById(10L)).thenReturn(Optional.of(taskInOrg(5L)));
        memberOf(5L, 1L, false);
        assertThatThrownBy(() -> authorizer.authorize("/topic/tasks/10", user(1L)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void resolvesProjectTopic() {
        Organization org = new Organization();
        org.setId(5L);
        Project project = new Project();
        project.setOrganization(org);
        when(projectRepository.findById(3L)).thenReturn(Optional.of(project));
        memberOf(5L, 1L, true);
        assertThatCode(() -> authorizer.authorize("/topic/projects/3/tasks", user(1L)))
                .doesNotThrowAnyException();
    }

    @Test
    void resolvesLiveTopicViaSessionCode() {
        SupabaseLiveSession session = new SupabaseLiveSession();
        session.setTaskId(10L);
        when(liveSessionService.getSessionByCode("ABCD1234")).thenReturn(Optional.of(session));
        when(taskRepository.findById(10L)).thenReturn(Optional.of(taskInOrg(5L)));
        memberOf(5L, 1L, true);
        assertThatCode(() -> authorizer.authorize("/topic/live/ABCD1234/chat", user(1L)))
                .doesNotThrowAnyException();
    }

    @Test
    void deniesMissingTask() {
        when(taskRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authorizer.authorize("/topic/tasks/404", user(1L)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deniesUnparseableId() {
        assertThatThrownBy(() -> authorizer.authorize("/topic/tasks/not-a-number", user(1L)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deniesNullUserOnScopedTopic() {
        assertThatThrownBy(() -> authorizer.authorize("/topic/organizations/7", null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void allowsUserDestinationWithoutChecks() {
        assertThatCode(() -> authorizer.authorize("/user/queue/tasks", user(1L)))
                .doesNotThrowAnyException();
        verifyNoInteractions(memberRepository, taskRepository, projectRepository,
                executionRepository, liveSessionService);
    }

    @Test
    void allowsUnknownTopicNamespace() {
        assertThatCode(() -> authorizer.authorize("/topic/heartbeat", user(1L)))
                .doesNotThrowAnyException();
        verifyNoInteractions(memberRepository);
    }
}
