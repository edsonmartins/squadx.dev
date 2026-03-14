package dev.squadx.service;

import dev.squadx.dto.agent.AgentRequest;
import dev.squadx.dto.agent.AgentResponse;
import dev.squadx.dto.common.PageResponse;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Agent;
import dev.squadx.model.Organization;
import dev.squadx.model.Squad;
import dev.squadx.model.User;
import dev.squadx.model.enums.AgentType;
import dev.squadx.model.enums.UserRole;
import dev.squadx.repository.AgentRepository;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.repository.SquadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private SquadRepository squadRepository;

    @Mock
    private OrganizationMemberRepository memberRepository;

    @InjectMocks
    private AgentService agentService;

    private User currentUser;
    private Organization organization;
    private Squad squad;
    private Agent agent;

    @BeforeEach
    void setUp() {
        currentUser = User.builder()
                .email("user@example.com")
                .password("encoded")
                .fullName("Current User")
                .role(UserRole.USER)
                .isActive(true)
                .build();
        currentUser.setId(1L);

        organization = Organization.builder()
                .name("Org")
                .slug("org")
                .build();
        organization.setId(100L);

        squad = Squad.builder()
                .name("Backend Squad")
                .organization(organization)
                .build();
        squad.setId(10L);

        agent = Agent.builder()
                .name("Coder Agent")
                .agentType(AgentType.BACKEND)
                .description("A backend agent")
                .modelId("gpt-4o")
                .systemPrompt("You are a coder")
                .maxTokens(4096)
                .temperature(0.7)
                .squad(squad)
                .capabilities(Set.of("code_generation"))
                .build();
        agent.setId(50L);
        agent.setCreatedAt(Instant.now());
        agent.setUpdatedAt(Instant.now());
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("should create agent with defaults when optional fields are null")
        void shouldCreateAgentWithDefaults() {
            AgentRequest request = AgentRequest.builder()
                    .name("New Agent")
                    .agentType(AgentType.FRONTEND)
                    .squadId(10L)
                    .build();

            when(squadRepository.findById(10L)).thenReturn(Optional.of(squad));
            when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);
            when(agentRepository.save(any(Agent.class))).thenAnswer(invocation -> {
                Agent saved = invocation.getArgument(0);
                saved.setId(51L);
                saved.setCreatedAt(Instant.now());
                return saved;
            });

            AgentResponse response = agentService.create(request, currentUser);

            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo("New Agent");
            assertThat(response.getModelId()).isEqualTo("gpt-4o");
            assertThat(response.getMaxTokens()).isEqualTo(4096);
            assertThat(response.getTemperature()).isEqualTo(0.7);
            verify(agentRepository).save(any(Agent.class));
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when squad not found")
        void shouldThrowWhenSquadNotFound() {
            AgentRequest request = AgentRequest.builder()
                    .name("Agent")
                    .agentType(AgentType.BACKEND)
                    .squadId(999L)
                    .build();

            when(squadRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> agentService.create(request, currentUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Squad not found");
        }

        @Test
        @DisplayName("should throw ForbiddenException when user has no org access")
        void shouldThrowWhenNoOrgAccess() {
            AgentRequest request = AgentRequest.builder()
                    .name("Agent")
                    .agentType(AgentType.BACKEND)
                    .squadId(10L)
                    .build();

            when(squadRepository.findById(10L)).thenReturn(Optional.of(squad));
            when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(false);

            assertThatThrownBy(() -> agentService.create(request, currentUser))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("User does not have access to this organization");

            verify(agentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getById()")
    class GetById {

        @Test
        @DisplayName("should return agent response when found and user has access")
        void shouldReturnAgent() {
            when(agentRepository.findById(50L)).thenReturn(Optional.of(agent));
            when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);

            AgentResponse response = agentService.getById(50L, currentUser);

            assertThat(response.getId()).isEqualTo(50L);
            assertThat(response.getName()).isEqualTo("Coder Agent");
            assertThat(response.getSquadId()).isEqualTo(10L);
            assertThat(response.getSquadName()).isEqualTo("Backend Squad");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when agent does not exist")
        void shouldThrowWhenNotFound() {
            when(agentRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> agentService.getById(999L, currentUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Agent not found");
        }
    }

    @Nested
    @DisplayName("getBySquadId()")
    class GetBySquadId {

        @Test
        @DisplayName("should return paged agents for a squad")
        void shouldReturnPagedAgents() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Agent> page = new PageImpl<>(List.of(agent), pageable, 1);

            when(squadRepository.findById(10L)).thenReturn(Optional.of(squad));
            when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);
            when(agentRepository.findBySquadId(10L, pageable)).thenReturn(page);

            PageResponse<AgentResponse> result = agentService.getBySquadId(10L, pageable, currentUser);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("should delete agent when found and user has access")
        void shouldDeleteAgent() {
            when(agentRepository.findById(50L)).thenReturn(Optional.of(agent));
            when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);

            agentService.delete(50L, currentUser);

            verify(agentRepository).delete(agent);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when agent does not exist")
        void shouldThrowWhenNotFound() {
            when(agentRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> agentService.delete(999L, currentUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Agent not found");

            verify(agentRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("toggleActive()")
    class ToggleActive {

        @Test
        @DisplayName("should toggle agent active status from true to false")
        void shouldToggleActive() {
            agent.setActive(true);

            when(agentRepository.findById(50L)).thenReturn(Optional.of(agent));
            when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);
            when(agentRepository.save(any(Agent.class))).thenAnswer(inv -> inv.getArgument(0));

            AgentResponse response = agentService.toggleActive(50L, currentUser);

            assertThat(response.isActive()).isFalse();
            verify(agentRepository).save(agent);
        }
    }
}
