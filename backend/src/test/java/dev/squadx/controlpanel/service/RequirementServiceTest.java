package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.dto.requirement.RequirementRequest;
import dev.squadx.controlpanel.dto.requirement.RequirementResponse;
import dev.squadx.controlpanel.dto.requirement.ScenarioRequest;
import dev.squadx.controlpanel.model.Change;
import dev.squadx.controlpanel.model.Requirement;
import dev.squadx.controlpanel.model.Scenario;
import dev.squadx.controlpanel.model.enums.RequirementType;
import dev.squadx.controlpanel.repository.ChangeRepository;
import dev.squadx.controlpanel.repository.RequirementRepository;
import dev.squadx.controlpanel.repository.ScenarioRepository;
import dev.squadx.controlpanel.repository.SpecTaskRepository;
import dev.squadx.exception.BadRequestException;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequirementServiceTest {

    @Mock private RequirementRepository requirementRepository;
    @Mock private ScenarioRepository scenarioRepository;
    @Mock private ChangeRepository changeRepository;
    @Mock private SpecTaskRepository specTaskRepository;
    @Mock private OrganizationMemberRepository memberRepository;

    @InjectMocks private RequirementService requirementService;

    private User user;
    private Change change;

    @BeforeEach
    void setUp() {
        user = User.builder().email("u@example.com").fullName("U").build();
        user.setId(1L);
        Organization org = Organization.builder().name("Org").slug("org").build();
        org.setId(100L);
        Project project = Project.builder().name("Proj").slug("proj").organization(org).build();
        project.setId(7L);
        change = Change.builder().project(project).build();
        change.setId(5L);
    }

    @Test
    void rejectsRequirementWithoutScenario() {
        RequirementRequest request = RequirementRequest.builder()
                .changeId(5L).type(RequirementType.ADDED).title("R sem cenário")
                .scenarios(List.of())
                .build();
        when(changeRepository.findById(5L)).thenReturn(Optional.of(change));
        when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> requirementService.create(request, user))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at least one acceptance scenario");

        verify(requirementRepository, never()).save(any());
    }

    @Test
    void createsAddedRequirementWithScenarioUncovered() {
        RequirementRequest request = RequirementRequest.builder()
                .changeId(5L).type(RequirementType.ADDED).title("Login")
                .scenarios(List.of(ScenarioRequest.builder()
                        .name("login inválido").when("credenciais erradas").then("rejeita").build()))
                .build();
        when(changeRepository.findById(5L)).thenReturn(Optional.of(change));
        when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);
        when(requirementRepository.findByChangeId(5L)).thenReturn(List.of());
        when(requirementRepository.save(any(Requirement.class))).thenAnswer(i -> {
            Requirement r = i.getArgument(0);
            r.setId(10L);
            return r;
        });
        when(scenarioRepository.findByRequirementId(10L)).thenReturn(List.of(
                Scenario.builder().requirement(null).name("login inválido")
                        .whenCondition("credenciais erradas").thenResult("rejeita").covered(false).build()));
        when(specTaskRepository.findByRequirementId(10L)).thenReturn(List.of());

        RequirementResponse response = requirementService.create(request, user);

        assertThat(response.getRequirementId()).isEqualTo("R1");
        assertThat(response.getScenarios()).hasSize(1);
        assertThat(response.getScenarios().get(0).isCovered()).isFalse();
        verify(scenarioRepository).save(any(Scenario.class));
    }
}
