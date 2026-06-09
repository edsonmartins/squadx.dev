package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.dto.spectask.SpecTaskRequest;
import dev.squadx.controlpanel.dto.spectask.SpecTaskResponse;
import dev.squadx.controlpanel.dto.spectask.SpecTaskTransitionRequest;
import dev.squadx.controlpanel.model.Change;
import dev.squadx.controlpanel.model.Requirement;
import dev.squadx.controlpanel.model.SpecTask;
import dev.squadx.controlpanel.model.enums.AssigneeType;
import dev.squadx.controlpanel.model.enums.EventSource;
import dev.squadx.controlpanel.model.enums.Pass5Result;
import dev.squadx.controlpanel.model.enums.SpecTaskStatus;
import dev.squadx.controlpanel.model.enums.TaskEventType;
import dev.squadx.controlpanel.repository.ChangeRepository;
import dev.squadx.controlpanel.repository.RequirementRepository;
import dev.squadx.controlpanel.repository.SpecTaskRepository;
import dev.squadx.exception.BadRequestException;
import dev.squadx.model.Agent;
import dev.squadx.model.Organization;
import dev.squadx.model.Project;
import dev.squadx.model.User;
import dev.squadx.repository.AgentRepository;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpecTaskServiceTest {

    @Mock private SpecTaskRepository specTaskRepository;
    @Mock private ChangeRepository changeRepository;
    @Mock private RequirementRepository requirementRepository;
    @Mock private UserRepository userRepository;
    @Mock private AgentRepository agentRepository;
    @Mock private OrganizationMemberRepository memberRepository;
    @Mock private SpecEventService specEventService;
    @Spy private SpecTaskStateMachine stateMachine = new SpecTaskStateMachine();

    @InjectMocks private SpecTaskService service;

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

    private SpecTask task(SpecTaskStatus status) {
        SpecTask t = SpecTask.builder().change(change).title("T").status(status)
                .pass5(Pass5Result.PENDING).build();
        t.setId(42L);
        return t;
    }

    private void mockTask(SpecTask t) {
        when(specTaskRepository.findById(42L)).thenReturn(Optional.of(t));
        when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);
    }

    @Test
    void startRecordsStartedEvent() {  // R4
        mockTask(task(SpecTaskStatus.A_FAZER));
        service.transition(42L,
                SpecTaskTransitionRequest.builder().status(SpecTaskStatus.EM_CURSO).build(), user);
        verify(specEventService).record(eq(42L), eq(TaskEventType.STARTED), eq(EventSource.MCP),
                anyString(), isNull(), any());
    }

    @Test
    void rejectsInvalidTransition() {  // R4
        mockTask(task(SpecTaskStatus.A_FAZER));
        assertThatThrownBy(() -> service.transition(42L,
                SpecTaskTransitionRequest.builder().status(SpecTaskStatus.EM_VALIDACAO).build(), user))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid transition");
        verify(specEventService, never()).record(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsPass5OnlyTargetFromTransition() {  // R5
        mockTask(task(SpecTaskStatus.EM_VALIDACAO));
        assertThatThrownBy(() -> service.transition(42L,
                SpecTaskTransitionRequest.builder().status(SpecTaskStatus.CONCLUIDA).build(), user))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("only be set by Pass 5");
    }

    @Test
    void blockRequiresReason() {  // R4
        mockTask(task(SpecTaskStatus.EM_CURSO));
        assertThatThrownBy(() -> service.transition(42L,
                SpecTaskTransitionRequest.builder().status(SpecTaskStatus.BLOQUEADA).build(), user))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void blockRecordsBlockedEventWithReason() {  // R4
        mockTask(task(SpecTaskStatus.EM_CURSO));
        service.transition(42L,
                SpecTaskTransitionRequest.builder().status(SpecTaskStatus.BLOQUEADA).note("waiting on API").build(),
                user);
        verify(specEventService).record(eq(42L), eq(TaskEventType.BLOCKED), eq(EventSource.MCP),
                anyString(), eq("waiting on API"), any());
    }

    @Test
    void pass5ApprovalRecordsApprovedEvent() {  // R5
        when(specTaskRepository.findById(42L)).thenReturn(Optional.of(task(SpecTaskStatus.EM_VALIDACAO)));
        service.applyPass5Outcome(42L, Pass5Result.PASS, null);
        verify(specEventService).record(eq(42L), eq(TaskEventType.PASS5_APPROVED), eq(EventSource.PASS5),
                anyString(), isNull(), any());
    }

    @Test
    void pass5FailureRecordsChangesEventWithCritique() {  // R5
        when(specTaskRepository.findById(42L)).thenReturn(Optional.of(task(SpecTaskStatus.EM_VALIDACAO)));
        service.applyPass5Outcome(42L, Pass5Result.FAIL, "cenário X sem teste");
        verify(specEventService).record(eq(42L), eq(TaskEventType.PASS5_CHANGES), eq(EventSource.PASS5),
                anyString(), eq("cenário X sem teste"), any());
    }

    @Test
    void createWithRequirementSetsRef() {  // R3
        Requirement req = Requirement.builder().change(change).requirementId("R1").build();
        req.setId(9L);
        when(changeRepository.findById(5L)).thenReturn(Optional.of(change));
        when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);
        when(requirementRepository.findById(9L)).thenReturn(Optional.of(req));
        when(specTaskRepository.save(any(SpecTask.class))).thenAnswer(i -> {
            SpecTask t = i.getArgument(0);
            t.setId(50L);
            return t;
        });

        SpecTaskResponse r = service.create(SpecTaskRequest.builder()
                .changeId(5L).requirementId(9L).title("Implementar login").build(), user);

        assertThat(r.getRequirementRef()).isEqualTo("R1");
    }

    @Test
    void createAgentAssigneeRequiresAgent() {  // R6
        when(changeRepository.findById(5L)).thenReturn(Optional.of(change));
        when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(SpecTaskRequest.builder()
                .changeId(5L).title("T").assigneeType(AssigneeType.AGENT).build(), user))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("assigned_agent_id");
    }

    @Test
    void createWithAgentAssignee() {  // R6
        Agent agent = Agent.builder().name("Backend Agent").build();
        agent.setId(3L);
        when(changeRepository.findById(5L)).thenReturn(Optional.of(change));
        when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);
        when(agentRepository.findById(3L)).thenReturn(Optional.of(agent));
        when(specTaskRepository.save(any(SpecTask.class))).thenAnswer(i -> i.getArgument(0));

        SpecTaskResponse r = service.create(SpecTaskRequest.builder()
                .changeId(5L).title("T").assigneeType(AssigneeType.AGENT).assignedAgentId(3L).build(), user);

        assertThat(r.getAssigneeType()).isEqualTo(AssigneeType.AGENT);
        assertThat(r.getAssignedAgentName()).isEqualTo("Backend Agent");
    }
}
