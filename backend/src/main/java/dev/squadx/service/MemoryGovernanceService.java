package dev.squadx.service;

import dev.squadx.dto.memory.MemorySkillRequest;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.integration.BrainSentryClient;
import dev.squadx.model.Execution;
import dev.squadx.model.Task;
import dev.squadx.model.User;
import dev.squadx.repository.ExecutionRepository;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemoryGovernanceService {

    private final BrainSentryClient brainSentryClient;
    private final OrganizationMemberRepository memberRepository;
    private final TaskRepository taskRepository;
    private final ExecutionRepository executionRepository;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTaskContext(Long taskId, User currentUser) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        Long organizationId = task.getProject().getOrganization().getId();
        validateUserAccess(organizationId, currentUser.getId());

        String query = String.join(" ",
                task.getTitle(),
                safe(task.getDescription()),
                "task " + taskId,
                "project " + task.getProject().getName()
        ).trim();

        List<Map<String, Object>> searched = brainSentryClient.searchMemories(organizationId, query, 12);
        return searched.stream()
                .filter(memory -> matchesScope(memory, organizationId, task.getProject().getId(), task.getAssignedAgent() != null ? task.getAssignedAgent().getId() : null))
                .limit(10)
                .map(this::normalizeMemory)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSkills(Long organizationId, Long projectId, Long agentId, String query, int limit, User currentUser) {
        validateUserAccess(organizationId, currentUser.getId());

        List<Map<String, Object>> source = (query != null && !query.isBlank())
                ? brainSentryClient.searchMemories(organizationId, query, Math.max(limit * 3, 15))
                : brainSentryClient.listMemories(organizationId, 0, Math.max(limit * 3, 25));

        return source.stream()
                .filter(this::isProceduralMemory)
                .filter(memory -> matchesScope(memory, organizationId, projectId, agentId))
                .sorted(Comparator.comparing((Map<String, Object> memory) -> String.valueOf(memory.getOrDefault("updatedAt", memory.getOrDefault("createdAt", "")))).reversed())
                .limit(Math.max(limit, 1))
                .map(this::normalizeSkill)
                .toList();
    }

    @Transactional
    public Map<String, Object> createSkill(MemorySkillRequest request, User currentUser) {
        validateUserAccess(request.getOrganizationId(), currentUser.getId());

        Map<String, Object> payload = buildSkillPayload(request, currentUser, null);
        Map<String, Object> created = brainSentryClient.createMemory(request.getOrganizationId(), payload);
        return normalizeSkill(created);
    }

    @Transactional
    public Map<String, Object> updateSkill(String memoryId, MemorySkillRequest request, User currentUser) {
        validateUserAccess(request.getOrganizationId(), currentUser.getId());

        Map<String, Object> payload = buildSkillPayload(request, currentUser, memoryId);
        Map<String, Object> updated = brainSentryClient.updateMemory(request.getOrganizationId(), memoryId, payload);
        return normalizeSkill(updated);
    }

    @Transactional
    public void deleteSkill(String memoryId, Long organizationId, User currentUser) {
        validateUserAccess(organizationId, currentUser.getId());
        brainSentryClient.deleteMemory(organizationId, memoryId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> searchHistory(Long organizationId, Long projectId, Long agentId,
                                             Long executionId, String query, int limit, User currentUser) {
        validateUserAccess(organizationId, currentUser.getId());

        Execution execution = null;
        if (executionId != null) {
            execution = executionRepository.findById(executionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Execution not found"));
            validateUserAccess(execution.getTask().getProject().getOrganization().getId(), currentUser.getId());
        }

        String effectiveQuery = resolveHistoryQuery(query, execution);
        List<Map<String, Object>> memories = effectiveQuery != null && !effectiveQuery.isBlank()
                ? brainSentryClient.searchMemories(organizationId, effectiveQuery, Math.max(limit, 1))
                : brainSentryClient.listMemories(organizationId, 0, Math.max(limit, 10));

        List<Map<String, Object>> filteredMemories = memories.stream()
                .filter(memory -> matchesScope(memory, organizationId, projectId, agentId))
                .limit(Math.max(limit, 1))
                .map(this::normalizeMemory)
                .toList();

        List<Execution> executions = projectId != null
                ? executionRepository.findTop20ByTask_Project_IdOrderByCreatedAtDesc(projectId)
                : executionRepository.findTop20ByTask_Project_Organization_IdOrderByCreatedAtDesc(organizationId);

        List<Map<String, Object>> executionHistory = executions.stream()
                .filter(item -> agentId == null || (item.getAgent() != null && Objects.equals(item.getAgent().getId(), agentId)))
                .filter(item -> matchesExecutionQuery(item, effectiveQuery))
                .limit(Math.max(limit, 1))
                .map(this::normalizeExecution)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("query", effectiveQuery);
        response.put("memories", filteredMemories);
        response.put("skills", filteredMemories.stream().filter(this::isProceduralMemory).toList());
        response.put("executions", executionHistory);
        response.put("active_sessions", brainSentryClient.listActiveSessions(organizationId));

        if (execution != null) {
            response.put("profile", brainSentryClient.getProfile(
                    organizationId,
                    execution.getAgent() != null ? "agent-" + execution.getAgent().getId() : "execution-" + execution.getId()
            ));
            if (execution.getBrainSentrySessionId() != null && !execution.getBrainSentrySessionId().isBlank()) {
                response.put("session", brainSentryClient.getSession(organizationId, execution.getBrainSentrySessionId()));
                response.put("session_cache", brainSentryClient.getSessionCache(organizationId, execution.getBrainSentrySessionId(), Math.max(limit, 10)));
            }
        }

        return response;
    }

    private String resolveHistoryQuery(String query, Execution execution) {
        if (query != null && !query.isBlank()) {
            return query;
        }
        if (execution == null) {
            return "execution memory history";
        }
        return String.join(" ",
                safe(execution.getTask().getTitle()),
                safe(execution.getTask().getDescription()),
                "execution " + execution.getId(),
                "task " + execution.getTask().getId()
        ).trim();
    }

    private boolean matchesExecutionQuery(Execution execution, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String haystack = String.join(" ",
                safe(execution.getTask().getTitle()),
                safe(execution.getTask().getDescription()),
                safe(execution.getResult()),
                safe(execution.getErrorMessage()),
                execution.getStatus().name()
        ).toLowerCase(Locale.ROOT);
        return haystack.contains(query.toLowerCase(Locale.ROOT));
    }

    private Map<String, Object> normalizeExecution(Execution execution) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("execution_id", execution.getId());
        item.put("task_id", execution.getTask().getId());
        item.put("task_title", execution.getTask().getTitle());
        item.put("agent_id", execution.getAgent() != null ? execution.getAgent().getId() : null);
        item.put("agent_name", execution.getAgent() != null ? execution.getAgent().getName() : null);
        item.put("status", execution.getStatus().name());
        item.put("brain_sentry_session_id", execution.getBrainSentrySessionId());
        item.put("started_at", execution.getStartedAt());
        item.put("completed_at", execution.getCompletedAt());
        item.put("summary", trimSummary(execution.getResult() != null ? execution.getResult() : execution.getErrorMessage()));
        return item;
    }

    private Map<String, Object> buildSkillPayload(MemorySkillRequest request, User currentUser, String memoryId) {
        List<String> tags = new ArrayList<>();
        tags.add("procedure");
        tags.add("organization:" + request.getOrganizationId());
        if (request.getProjectId() != null) {
            tags.add("project:" + request.getProjectId());
        }
        if (request.getAgentId() != null) {
            tags.add("agent:" + request.getAgentId());
        }
        if (request.getAgentType() != null && !request.getAgentType().isBlank()) {
            tags.add("agent-type:" + request.getAgentType().toLowerCase(Locale.ROOT));
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("organizationId", request.getOrganizationId());
        metadata.put("projectId", request.getProjectId());
        metadata.put("agentId", request.getAgentId());
        metadata.put("agentType", request.getAgentType());
        metadata.put("steps", request.getSteps() != null ? request.getSteps() : List.of());
        metadata.put("filesModified", request.getFilesModified() != null ? request.getFilesModified() : List.of());
        metadata.put("managedBy", "squadx");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", buildSkillContent(request));
        payload.put("summary", request.getTitle() + ": " + request.getSummary());
        payload.put("category", request.isAntipattern() ? "ANTIPATTERN" : "PATTERN");
        payload.put("importance", "IMPORTANT");
        payload.put("memoryType", "PROCEDURAL");
        payload.put("tags", tags);
        payload.put("metadata", metadata);
        payload.put("sourceType", "squadx-procedure");
        payload.put("sourceReference", memoryId != null ? "skill:" + memoryId : "skill:" + slugify(request.getTitle()));
        payload.put("createdBy", currentUser.getEmail());
        return payload;
    }

    private String buildSkillContent(MemorySkillRequest request) {
        List<String> lines = new ArrayList<>();
        lines.add("Procedure: " + request.getTitle());
        lines.add("Summary: " + request.getSummary());
        if (request.getContent() != null && !request.getContent().isBlank()) {
            lines.add("");
            lines.add(request.getContent().trim());
        }
        if (request.getSteps() != null && !request.getSteps().isEmpty()) {
            lines.add("");
            lines.add("Steps:");
            request.getSteps().stream()
                    .filter(step -> step != null && !step.isBlank())
                    .forEach(step -> lines.add("- " + step.trim()));
        }
        if (request.getFilesModified() != null && !request.getFilesModified().isEmpty()) {
            lines.add("");
            lines.add("Files touched: " + String.join(", ", request.getFilesModified()));
        }
        return String.join("\n", lines);
    }

    private boolean matchesScope(Map<String, Object> memory, Long organizationId, Long projectId, Long agentId) {
        Map<String, Object> metadata = nestedMap(memory.get("metadata"));
        String org = normalize(metadata.get("organizationId"));
        String project = normalize(metadata.get("projectId"));
        String agent = normalize(metadata.get("agentId"));

        if (organizationId != null && org != null && !Objects.equals(org, String.valueOf(organizationId))) {
            return false;
        }
        if (projectId != null && project != null && !Objects.equals(project, String.valueOf(projectId))) {
            return false;
        }
        if (agentId != null && agent != null && !Objects.equals(agent, String.valueOf(agentId))) {
            return false;
        }

        List<String> tags = tags(memory);
        if (organizationId != null && org == null && !tags.contains("organization:" + organizationId)) {
            return false;
        }
        if (projectId != null && project == null && !tags.contains("project:" + projectId)) {
            return false;
        }
        return agentId == null || agent != null || tags.contains("agent:" + agentId);
    }

    private boolean isProceduralMemory(Map<String, Object> memory) {
        String memoryType = String.valueOf(memory.getOrDefault("memoryType", memory.getOrDefault("type", ""))).toUpperCase(Locale.ROOT);
        String category = String.valueOf(memory.getOrDefault("category", "")).toUpperCase(Locale.ROOT);
        List<String> tags = tags(memory);
        return "PROCEDURAL".equals(memoryType)
                || "PATTERN".equals(category)
                || "ANTIPATTERN".equals(category)
                || tags.contains("procedure");
    }

    private Map<String, Object> normalizeSkill(Map<String, Object> memory) {
        Map<String, Object> item = normalizeMemory(memory);
        Map<String, Object> metadata = nestedMap(memory.get("metadata"));
        item.put("organization_id", metadata.get("organizationId"));
        item.put("project_id", metadata.get("projectId"));
        item.put("agent_id", metadata.get("agentId"));
        item.put("agent_type", metadata.get("agentType"));
        item.put("steps", metadata.getOrDefault("steps", List.of()));
        item.put("files_modified", metadata.getOrDefault("filesModified", List.of()));
        item.put("antipattern", "ANTIPATTERN".equals(String.valueOf(memory.getOrDefault("category", "")).toUpperCase(Locale.ROOT)));
        // Distinguish human-authored skills from BrainSentry-learned procedural memory.
        boolean authored = "squadx-procedure".equals(String.valueOf(item.get("source_type")))
                || "squadx".equals(String.valueOf(metadata.get("managedBy")));
        item.put("authored", authored);
        return item;
    }

    private Map<String, Object> normalizeMemory(Map<String, Object> memory) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", memory.get("id"));
        item.put("content", memory.get("content"));
        item.put("summary", memory.get("summary"));
        item.put("category", memory.get("category"));
        item.put("importance", memory.get("importance"));
        item.put("memory_type", memory.getOrDefault("memoryType", memory.get("type")));
        item.put("tags", tags(memory));
        item.put("metadata", nestedMap(memory.get("metadata")));
        item.put("created_at", memory.getOrDefault("createdAt", memory.get("created_at")));
        item.put("updated_at", memory.getOrDefault("updatedAt", memory.get("updated_at")));
        item.put("source_type", memory.getOrDefault("sourceType", memory.get("source_type")));
        item.put("source_reference", memory.getOrDefault("sourceReference", memory.get("source_reference")));
        item.put("created_by", memory.getOrDefault("createdBy", memory.get("created_by")));
        return item;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .filter(entry -> entry.getKey() != null)
                    .collect(Collectors.toMap(
                            entry -> String.valueOf(entry.getKey()),
                            Map.Entry::getValue,
                            (left, right) -> right,
                            LinkedHashMap::new
                    ));
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<String> tags(Map<String, Object> memory) {
        Object raw = memory.get("tags");
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).map(String::toLowerCase).toList();
        }
        return List.of();
    }

    private String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String trimSummary(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() > 240 ? value.substring(0, 240) + "..." : value;
    }

    private String slugify(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    private void validateUserAccess(Long organizationId, Long userId) {
        if (organizationId == null || !memberRepository.existsByOrganizationIdAndUserId(organizationId, userId)) {
            throw new ForbiddenException("User does not have access to this organization");
        }
    }
}
