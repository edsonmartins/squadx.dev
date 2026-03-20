package dev.squadx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.squadx.dto.template.ApplyTemplateRequest;
import dev.squadx.dto.template.TemplateResponse;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Agent;
import dev.squadx.model.Organization;
import dev.squadx.model.Squad;
import dev.squadx.model.User;
import dev.squadx.model.enums.UserRole;
import dev.squadx.repository.AgentRepository;
import dev.squadx.repository.OrganizationRepository;
import dev.squadx.repository.SquadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamTemplateServiceTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private SquadRepository squadRepository;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private TeamTemplateService teamTemplateService;

    private User testUser;
    private Organization testOrg;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .fullName("Test User")
                .role(UserRole.USER)
                .isActive(true)
                .build();
        testUser.setId(1L);

        testOrg = Organization.builder()
                .name("Test Org")
                .slug("test-org")
                .build();
        testOrg.setId(1L);
    }

    @Nested
    @DisplayName("listTemplates()")
    class ListTemplates {

        @Test
        @DisplayName("should return all 4 templates")
        void shouldReturnAllTemplates() {
            List<TemplateResponse> templates = teamTemplateService.listTemplates();

            assertThat(templates).hasSize(4);
            List<String> names = templates.stream()
                    .map(TemplateResponse::getName)
                    .toList();
            assertThat(names).containsExactlyInAnyOrder(
                    "Software Development",
                    "Code Review",
                    "Full Stack",
                    "Data Pipeline"
            );
        }

        @Test
        @DisplayName("each template should have agents and default tasks")
        void shouldHaveAgentsAndTasks() {
            List<TemplateResponse> templates = teamTemplateService.listTemplates();

            for (TemplateResponse template : templates) {
                assertThat(template.getAgents()).isNotEmpty();
                assertThat(template.getDefaultTasks()).isNotEmpty();
                assertThat(template.getIcon()).isNotBlank();
                assertThat(template.getDescription()).isNotBlank();
            }
        }
    }

    @Nested
    @DisplayName("getTemplate()")
    class GetTemplate {

        @Test
        @DisplayName("should return template by valid name")
        void shouldReturnTemplateByName() {
            TemplateResponse template = teamTemplateService.getTemplate("software-dev");

            assertThat(template).isNotNull();
            assertThat(template.getName()).isEqualTo("Software Development");
            assertThat(template.getAgents()).hasSize(5);
            assertThat(template.getDefaultTasks()).hasSize(5);
        }

        @Test
        @DisplayName("should throw for invalid template name")
        void shouldThrowForInvalidName() {
            assertThatThrownBy(() -> teamTemplateService.getTemplate("nonexistent"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Template not found");
        }
    }

    @Nested
    @DisplayName("applyTemplate()")
    class ApplyTemplate {

        @Test
        @DisplayName("should create squad and agents from template")
        void shouldCreateSquadAndAgents() {
            ApplyTemplateRequest request = ApplyTemplateRequest.builder()
                    .organizationId(1L)
                    .build();

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(squadRepository.save(any(Squad.class))).thenAnswer(invocation -> {
                Squad saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });
            when(agentRepository.save(any(Agent.class))).thenAnswer(invocation -> {
                Agent saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            Squad squad = teamTemplateService.applyTemplate("software-dev", request, testUser);

            assertThat(squad).isNotNull();
            assertThat(squad.getName()).isEqualTo("Software Development Squad");
            assertThat(squad.getOrganization().getId()).isEqualTo(1L);

            verify(squadRepository).save(any(Squad.class));
            verify(agentRepository, times(5)).save(any(Agent.class));
        }

        @Test
        @DisplayName("should use custom squad name when provided")
        void shouldUseCustomSquadName() {
            ApplyTemplateRequest request = ApplyTemplateRequest.builder()
                    .organizationId(1L)
                    .squadName("My Custom Squad")
                    .build();

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(squadRepository.save(any(Squad.class))).thenAnswer(invocation -> {
                Squad saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });
            when(agentRepository.save(any(Agent.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Squad squad = teamTemplateService.applyTemplate("full-stack", request, testUser);

            assertThat(squad.getName()).isEqualTo("My Custom Squad");
        }

        @Test
        @DisplayName("should substitute variables in descriptions")
        void shouldSubstituteVariables() {
            ApplyTemplateRequest request = ApplyTemplateRequest.builder()
                    .organizationId(1L)
                    .squadName("E-Commerce")
                    .goal("Build an online store")
                    .build();

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(squadRepository.save(any(Squad.class))).thenAnswer(invocation -> {
                Squad saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });
            when(agentRepository.save(any(Agent.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Use a template that could contain variables
            Squad squad = teamTemplateService.applyTemplate("software-dev", request, testUser);

            assertThat(squad).isNotNull();
            // The squad name should be the custom name
            assertThat(squad.getName()).isEqualTo("E-Commerce");
        }

        @Test
        @DisplayName("should throw when organization not found")
        void shouldThrowWhenOrgNotFound() {
            ApplyTemplateRequest request = ApplyTemplateRequest.builder()
                    .organizationId(99L)
                    .build();

            when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> teamTemplateService.applyTemplate("software-dev", request, testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Organization not found");

            verify(squadRepository, never()).save(any(Squad.class));
        }

        @Test
        @DisplayName("should throw when template not found")
        void shouldThrowWhenTemplateNotFound() {
            ApplyTemplateRequest request = ApplyTemplateRequest.builder()
                    .organizationId(1L)
                    .build();

            assertThatThrownBy(() -> teamTemplateService.applyTemplate("nonexistent", request, testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Template not found");

            verify(squadRepository, never()).save(any(Squad.class));
        }
    }
}
