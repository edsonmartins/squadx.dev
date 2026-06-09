package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.model.Change;
import dev.squadx.controlpanel.model.Requirement;
import dev.squadx.controlpanel.model.Scenario;
import dev.squadx.controlpanel.repository.ScenarioRepository;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.model.Organization;
import dev.squadx.model.Project;
import dev.squadx.model.User;
import dev.squadx.repository.OrganizationMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoverageServiceTest {

    @Mock private ScenarioRepository scenarioRepository;
    @Mock private OrganizationMemberRepository memberRepository;
    @InjectMocks private CoverageService service;

    private User user;
    private Scenario scenario;

    @BeforeEach
    void setUp() {
        user = User.builder().email("u@example.com").fullName("U").build();
        user.setId(1L);
        Organization org = Organization.builder().name("Org").slug("org").build();
        org.setId(100L);
        Project project = Project.builder().name("Proj").slug("proj").organization(org).build();
        project.setId(7L);
        Change change = Change.builder().project(project).build();
        change.setId(5L);
        Requirement req = Requirement.builder().change(change).requirementId("R1").build();
        req.setId(9L);
        scenario = Scenario.builder().requirement(req).name("s")
                .whenCondition("w").thenResult("t").covered(false).build();
        scenario.setId(3L);
    }

    @Test
    void marksScenarioCovered() {
        when(scenarioRepository.findById(3L)).thenReturn(Optional.of(scenario));
        when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);

        service.setCovered(3L, true, user);

        assertThat(scenario.isCovered()).isTrue();
        verify(scenarioRepository).save(scenario);
    }

    @Test
    void deniesWithoutAccess() {
        when(scenarioRepository.findById(3L)).thenReturn(Optional.of(scenario));
        when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> service.setCovered(3L, true, user))
                .isInstanceOf(ForbiddenException.class);
        verify(scenarioRepository, never()).save(any());
    }
}
