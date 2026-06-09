package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.dto.change.ChangeRequest;
import dev.squadx.controlpanel.dto.change.ChangeResponse;
import dev.squadx.controlpanel.model.Change;
import dev.squadx.controlpanel.model.enums.ChangePhase;
import dev.squadx.controlpanel.model.enums.SpecTaskStatus;
import dev.squadx.controlpanel.repository.ChangeRepository;
import dev.squadx.controlpanel.repository.SpecTaskRepository;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.model.Organization;
import dev.squadx.model.Project;
import dev.squadx.model.User;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChangeServiceTest {

    @Mock private ChangeRepository changeRepository;
    @Mock private SpecTaskRepository specTaskRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private OrganizationMemberRepository memberRepository;

    @InjectMocks private ChangeService changeService;

    private User user;
    private Project project;

    @BeforeEach
    void setUp() {
        user = User.builder().email("u@example.com").fullName("U").build();
        user.setId(1L);
        Organization org = Organization.builder().name("Org").slug("org").build();
        org.setId(100L);
        project = Project.builder().name("Proj").slug("proj").organization(org).build();
        project.setId(7L);
    }

    @Test
    void createsChangeInSpecPhase() {  // R1
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);
        when(changeRepository.save(any(Change.class))).thenAnswer(i -> {
            Change c = i.getArgument(0);
            c.setId(5L);
            return c;
        });

        ChangeResponse r = changeService.create(
                ChangeRequest.builder().projectId(7L).module("auth").build(), user);

        assertThat(r.getProjectId()).isEqualTo(7L);
        assertThat(r.getPhase()).isEqualTo(ChangePhase.SPEC);
    }

    @Test
    void createForbiddenWithoutAccess() {
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> changeService.create(
                ChangeRequest.builder().projectId(7L).build(), user))
                .isInstanceOf(ForbiddenException.class);

        verify(changeRepository, never()).save(any());
    }

    @Test
    void whereWeAreAggregatesByStatus() {  // R7
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);
        when(specTaskRepository.countByStatusForProject(7L)).thenReturn(List.of(
                new Object[]{SpecTaskStatus.CONCLUIDA, 2L},
                new Object[]{SpecTaskStatus.EM_CURSO, 1L}));

        Map<String, Object> r = changeService.whereWeAre(7L, user);

        assertThat(r.get("total")).isEqualTo(3L);
        assertThat(r.get("concluidas")).isEqualTo(2L);
        assertThat((double) r.get("progress")).isEqualTo(2.0 / 3.0);
    }
}
