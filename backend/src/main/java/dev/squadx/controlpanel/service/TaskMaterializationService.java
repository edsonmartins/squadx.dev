package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.parser.CandidateTask;
import dev.squadx.controlpanel.parser.TaskDecisionParser;
import dev.squadx.model.Project;
import dev.squadx.model.Task;
import dev.squadx.model.enums.DecisionSourceKind;
import dev.squadx.model.enums.TaskStatus;
import dev.squadx.repository.ProjectRepository;
import dev.squadx.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Materializa tarefas a partir de decisões do corpus (RFC-0007, T-0010-5).
 *
 * <p>Portão de entrada: só entram no board spec-native tarefas com {@code source_ref}
 * — arquivo + âncora da decisão. Re-parse idempotente: mesma âncora ⇒ upsert, nunca duplicata.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskMaterializationService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final TaskDecisionParser parser;

    /**
     * Deriva (upsert) tarefas de um documento de decisão para um projeto.
     *
     * @param content    conteúdo markdown do arquivo
     * @param path       caminho do arquivo (ex.: "docs/rfc/RFC-0007-x.md")
     * @param sourceKind tipo de decisão
     * @param projectId  projeto de destino
     * @return ids das tarefas materializadas/atualizadas
     */
    @Transactional
    public List<Long> materialize(String content, String path, String sourceKind, Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new dev.squadx.exception.ResourceNotFoundException("Project not found"));
        List<CandidateTask> candidates = parser.parse(content, path, sourceKind);
        if (candidates.isEmpty()) {
            log.info("Nenhuma tarefa derivada de {}", path);
            return List.of();
        }

        List<Long> ids = new java.util.ArrayList<>();
        for (CandidateTask candidate : candidates) {
            Task task = taskRepository.findBySourceRef(candidate.sourceRef()).orElse(null);
            if (task == null) {
                task = Task.builder()
                        .title(candidate.title() == null || candidate.title().isBlank()
                                ? candidate.id() : candidate.title())
                        .sourceRef(candidate.sourceRef())
                        .sourceKind(DecisionSourceKind.valueOf(candidate.sourceKind()))
                        .status(TaskStatus.TODO)
                        .project(project)
                        .build(); // sem createdBy: a decisão é a autora (Git-first)
            } else {
                // upsert: título/prioridade na mesma âncora
                task.setTitle(candidate.title());
            }
            ids.add(taskRepository.save(task).getId());
            log.info("Materializada task {} (origem {})", ids.get(ids.size() - 1), candidate.sourceRef());
        }
        return ids;
    }
}
