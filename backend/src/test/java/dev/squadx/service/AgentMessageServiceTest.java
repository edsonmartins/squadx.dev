package dev.squadx.service;

import dev.squadx.dto.agent.AgentMessageRequest;
import dev.squadx.dto.agent.AgentMessageResponse;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.*;
import dev.squadx.model.enums.AgentMessageType;
import dev.squadx.model.enums.AgentType;
import dev.squadx.model.enums.UserRole;
import dev.squadx.repository.AgentMessageRepository;
import dev.squadx.repository.AgentRepository;
import dev.squadx.repository.ExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentMessageServiceTest {

    @Mock
    private AgentMessageRepository messageRepository;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private ExecutionRepository executionRepository;

    @Mock
    private WebSocketEventService webSocketEventService;

    @InjectMocks
    private AgentMessageService agentMessageService;

    private User testUser;
    private Organization testOrg;
    private Squad testSquad;
    private Agent agentA;
    private Agent agentB;
    private Agent agentC;
    private Execution testExecution;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .password("encoded")
                .fullName("Test User")
                .role(UserRole.USER)
                .build();
        testUser.setId(1L);

        testOrg = Organization.builder()
                .name("Test Org")
                .slug("test-org")
                .build();
        testOrg.setId(1L);

        testSquad = Squad.builder()
                .name("Test Squad")
                .organization(testOrg)
                .build();
        testSquad.setId(10L);

        agentA = Agent.builder()
                .name("Agent A")
                .agentType(AgentType.BACKEND)
                .squad(testSquad)
                .build();
        agentA.setId(1L);
        agentA.setCreatedAt(Instant.now());

        agentB = Agent.builder()
                .name("Agent B")
                .agentType(AgentType.FRONTEND)
                .squad(testSquad)
                .build();
        agentB.setId(2L);
        agentB.setCreatedAt(Instant.now());

        agentC = Agent.builder()
                .name("Agent C")
                .agentType(AgentType.QA)
                .squad(testSquad)
                .build();
        agentC.setId(3L);
        agentC.setCreatedAt(Instant.now());

        Task testTask = Task.builder()
                .title("Test Task")
                .build();
        testTask.setId(100L);

        testExecution = Execution.builder()
                .task(testTask)
                .agent(agentA)
                .build();
        testExecution.setId(50L);
    }

    @Test
    @DisplayName("send should create message and trigger WebSocket event")
    void sendShouldCreateMessageAndTriggerWebSocket() {
        AgentMessageRequest request = AgentMessageRequest.builder()
                .fromAgentId(1L)
                .toAgentId(2L)
                .messageType("TASK_UPDATE")
                .content("Hello Agent B")
                .build();

        when(agentRepository.findById(1L)).thenReturn(Optional.of(agentA));
        when(agentRepository.findById(2L)).thenReturn(Optional.of(agentB));
        when(messageRepository.save(any(AgentMessage.class))).thenAnswer(invocation -> {
            AgentMessage msg = invocation.getArgument(0);
            msg.setId(1L);
            msg.setCreatedAt(Instant.now());
            return msg;
        });

        AgentMessageResponse response = agentMessageService.send(request, testUser);

        assertThat(response).isNotNull();
        assertThat(response.getFromAgentId()).isEqualTo(1L);
        assertThat(response.getToAgentId()).isEqualTo(2L);
        assertThat(response.getContent()).isEqualTo("Hello Agent B");
        assertThat(response.getMessageType()).isEqualTo("TASK_UPDATE");
        assertThat(response.isBroadcast()).isFalse();

        verify(messageRepository).save(any(AgentMessage.class));
        verify(webSocketEventService).sendOrganizationEvent(eq(1L), eq("agent_message"), any());
    }

    @Test
    @DisplayName("broadcast should send to all squad agents except sender")
    void broadcastShouldSendToAllSquadAgents() {
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agentA));
        when(executionRepository.findById(50L)).thenReturn(Optional.of(testExecution));
        when(agentRepository.findBySquadIdAndIsActiveTrue(10L))
                .thenReturn(List.of(agentA, agentB, agentC));
        when(messageRepository.save(any(AgentMessage.class))).thenAnswer(invocation -> {
            AgentMessage msg = invocation.getArgument(0);
            msg.setId(System.nanoTime());
            msg.setCreatedAt(Instant.now());
            return msg;
        });

        List<AgentMessageResponse> responses = agentMessageService.broadcast(1L, 50L, "Broadcast message");

        assertThat(responses).hasSize(2);
        assertThat(responses).allMatch(r -> r.isBroadcast());
        assertThat(responses).noneMatch(r -> r.getToAgentId().equals(1L));

        verify(messageRepository, times(2)).save(any(AgentMessage.class));
        verify(webSocketEventService).sendOrganizationEvent(eq(1L), eq("agent_broadcast"), any());
    }

    @Test
    @DisplayName("getUnread should return only unread messages")
    void getUnreadShouldReturnOnlyUnread() {
        AgentMessage unreadMsg = AgentMessage.builder()
                .fromAgent(agentA)
                .toAgent(agentB)
                .messageType(AgentMessageType.TASK_UPDATE)
                .content("Unread message")
                .isRead(false)
                .build();
        unreadMsg.setId(1L);
        unreadMsg.setCreatedAt(Instant.now());

        when(messageRepository.findByToAgentIdAndIsReadFalseOrderByCreatedAtAsc(2L))
                .thenReturn(List.of(unreadMsg));

        List<AgentMessageResponse> responses = agentMessageService.getUnread(2L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).isRead()).isFalse();
        assertThat(responses.get(0).getContent()).isEqualTo("Unread message");
    }

    @Test
    @DisplayName("markRead should update message to read")
    void markReadShouldUpdateMessage() {
        AgentMessage message = AgentMessage.builder()
                .fromAgent(agentA)
                .toAgent(agentB)
                .messageType(AgentMessageType.TASK_UPDATE)
                .content("Some message")
                .isRead(false)
                .build();
        message.setId(1L);

        when(messageRepository.findById(1L)).thenReturn(Optional.of(message));
        when(messageRepository.save(any(AgentMessage.class))).thenReturn(message);

        agentMessageService.markRead(1L);

        ArgumentCaptor<AgentMessage> captor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(messageRepository).save(captor.capture());
        assertThat(captor.getValue().isRead()).isTrue();
    }

    @Test
    @DisplayName("markAllRead should call repository bulk update")
    void markAllReadShouldCallBulkUpdate() {
        when(messageRepository.markAllReadByAgentId(2L)).thenReturn(5);

        agentMessageService.markAllRead(2L);

        verify(messageRepository).markAllReadByAgentId(2L);
    }

    @Test
    @DisplayName("send with invalid agent should throw ResourceNotFoundException")
    void sendWithInvalidAgentShouldThrow() {
        AgentMessageRequest request = AgentMessageRequest.builder()
                .fromAgentId(999L)
                .toAgentId(2L)
                .content("Hello")
                .build();

        when(agentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentMessageService.send(request, testUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Source agent not found");
    }

    @Test
    @DisplayName("send with invalid target agent should throw ResourceNotFoundException")
    void sendWithInvalidTargetAgentShouldThrow() {
        AgentMessageRequest request = AgentMessageRequest.builder()
                .fromAgentId(1L)
                .toAgentId(999L)
                .content("Hello")
                .build();

        when(agentRepository.findById(1L)).thenReturn(Optional.of(agentA));
        when(agentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentMessageService.send(request, testUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Target agent not found");
    }

    @Test
    @DisplayName("getByExecution should return ordered message history")
    void getByExecutionShouldReturnOrderedHistory() {
        AgentMessage msg1 = AgentMessage.builder()
                .fromAgent(agentA)
                .toAgent(agentB)
                .execution(testExecution)
                .messageType(AgentMessageType.TASK_UPDATE)
                .content("First message")
                .build();
        msg1.setId(1L);
        msg1.setCreatedAt(Instant.now().minusSeconds(60));

        AgentMessage msg2 = AgentMessage.builder()
                .fromAgent(agentB)
                .toAgent(agentA)
                .execution(testExecution)
                .messageType(AgentMessageType.RESULT)
                .content("Second message")
                .build();
        msg2.setId(2L);
        msg2.setCreatedAt(Instant.now());

        when(messageRepository.findByExecutionIdOrderByCreatedAtAsc(50L))
                .thenReturn(List.of(msg1, msg2));

        List<AgentMessageResponse> responses = agentMessageService.getByExecution(50L);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getContent()).isEqualTo("First message");
        assertThat(responses.get(1).getContent()).isEqualTo("Second message");
        assertThat(responses.get(0).getMessageType()).isEqualTo("TASK_UPDATE");
        assertThat(responses.get(1).getMessageType()).isEqualTo("RESULT");
    }
}
