package dev.squadx.service;

import dev.squadx.dto.memory.MemorySkillRequest;
import dev.squadx.integration.BrainSentryClient;
import dev.squadx.model.Execution;
import dev.squadx.model.Organization;
import dev.squadx.model.Project;
import dev.squadx.model.Task;
import dev.squadx.model.User;
import dev.squadx.model.enums.ExecutionStatus;
import dev.squadx.repository.ExecutionRepository;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.repository.TaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryGovernanceServiceTest {

    @Mock
    private BrainSentryClient brainSentryClient;

    @Mock
    private OrganizationMemberRepository memberRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ExecutionRepository executionRepository;

    @InjectMocks
    private MemoryGovernanceService memoryGovernanceService;

    @Test
    @DisplayName("listSkills should keep only procedural memories in scope")
    void listSkillsShouldFilterProceduralMemories() {
        User user = user();
        when(memberRepository.existsByOrganizationIdAndUserId(7L, 11L)).thenReturn(true);
        when(brainSentryClient.listMemories(7L, 0, 30)).thenReturn(List.of(
                Map.of(
                        "id", "skill-1",
                        "summary", "Review migration failures",
                        "content", "Steps to inspect Flyway errors",
                        "category", "PATTERN",
                        "memoryType", "PROCEDURAL",
                        "tags", List.of("procedure", "organization:7"),
                        "metadata", Map.of("organizationId", 7, "steps", List.of("Inspect logs"))
                ),
                Map.of(
                        "id", "memory-2",
                        "summary", "General note",
                        "content", "Non procedural note",
                        "category", "KNOWLEDGE",
                        "memoryType", "SEMANTIC",
                        "tags", List.of("note"),
                        "metadata", Map.of("organizationId", 7)
                )
        ));

        List<Map<String, Object>> skills = memoryGovernanceService.listSkills(7L, null, null, null, 10, user);

        assertThat(skills).hasSize(1);
        assertThat(skills.getFirst().get("id")).isEqualTo("skill-1");
        assertThat(skills.getFirst().get("steps")).isEqualTo(List.of("Inspect logs"));
    }

    @Test
    @DisplayName("searchHistory should return execution summaries and session cache for an execution")
    void searchHistoryShouldIncludeExecutionSessionData() {
        User user = user();
        Execution execution = execution();

        when(memberRepository.existsByOrganizationIdAndUserId(7L, 11L)).thenReturn(true);
        when(executionRepository.findById(31L)).thenReturn(Optional.of(execution));
        when(brainSentryClient.searchMemories(eq(7L), eq("Investigate payment retries"), anyInt())).thenReturn(List.of(
                Map.of(
                        "id", "mem-1",
                        "summary", "Retry strategy",
                        "content", "Use idempotent retries",
                        "category", "PATTERN",
                        "memoryType", "PROCEDURAL",
                        "tags", List.of("procedure", "organization:7"),
                        "metadata", Map.of("organizationId", 7)
                )
        ));
        when(executionRepository.findTop20ByTask_Project_Organization_IdOrderByCreatedAtDesc(7L)).thenReturn(List.of(execution));
        when(brainSentryClient.getProfile(7L, "execution-31")).thenReturn(Map.of("staticProfile", Map.of("summary", "Executor profile")));
        when(brainSentryClient.getSession(7L, "session-31")).thenReturn(Map.of("id", "session-31", "status", "ACTIVE"));
        when(brainSentryClient.getSessionCache(7L, "session-31", 10)).thenReturn(List.of(
                Map.of("query", "Investigate payment retries", "response", "Retry with backoff")
        ));
        when(brainSentryClient.listActiveSessions(7L)).thenReturn(List.of(Map.of("id", "session-31")));

        Map<String, Object> history = memoryGovernanceService.searchHistory(7L, null, null, 31L, "Investigate payment retries", 5, user);

        assertThat((List<?>) history.get("memories")).hasSize(1);
        assertThat((List<?>) history.get("executions")).hasSize(1);
        assertThat(history.get("profile")).isEqualTo(Map.of("staticProfile", Map.of("summary", "Executor profile")));
        assertThat((List<?>) history.get("session_cache")).hasSize(1);
    }

    @Test
    @DisplayName("createSkill should send procedural payload to BrainSentry")
    void createSkillShouldBuildProceduralPayload() {
        User user = user();
        when(memberRepository.existsByOrganizationIdAndUserId(7L, 11L)).thenReturn(true);
        when(brainSentryClient.createMemory(eq(7L), org.mockito.ArgumentMatchers.anyMap())).thenReturn(Map.of(
                "id", "skill-7",
                "summary", "Review migrations: Check Flyway drift",
                "content", "Procedure: Review migrations",
                "category", "PATTERN",
                "memoryType", "PROCEDURAL",
                "tags", List.of("procedure", "organization:7"),
                "metadata", Map.of("organizationId", 7, "steps", List.of("Inspect logs"))
        ));

        MemorySkillRequest request = new MemorySkillRequest();
        request.setOrganizationId(7L);
        request.setTitle("Review migrations");
        request.setSummary("Check Flyway drift");
        request.setSteps(List.of("Inspect logs"));

        Map<String, Object> created = memoryGovernanceService.createSkill(request, user);

        assertThat(created.get("id")).isEqualTo("skill-7");
        assertThat(created.get("steps")).isEqualTo(List.of("Inspect logs"));
    }

    private User user() {
        User user = new User();
        user.setId(11L);
        user.setEmail("admin@squadx.dev");
        return user;
    }

    private Execution execution() {
        Organization organization = Organization.builder().name("Org").build();
        organization.setId(7L);

        Project project = Project.builder().name("Payments").organization(organization).build();
        project.setId(21L);

        Task task = Task.builder()
                .title("Investigate payment retries")
                .description("Find the retry storm root cause")
                .project(project)
                .build();
        task.setId(41L);

        Execution execution = Execution.builder()
                .task(task)
                .status(ExecutionStatus.COMPLETED)
                .brainSentrySessionId("session-31")
                .result("Retry storm fixed with capped backoff")
                .startedAt(Instant.now())
                .completedAt(Instant.now())
                .build();
        execution.setId(31L);
        return execution;
    }
}
