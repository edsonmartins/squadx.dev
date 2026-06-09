package dev.squadx.controlpanel.materialization;

import dev.squadx.controlpanel.model.Change;
import dev.squadx.controlpanel.model.Requirement;
import dev.squadx.controlpanel.model.Scenario;
import dev.squadx.controlpanel.model.SpecTask;
import dev.squadx.controlpanel.model.enums.RequirementType;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Renderiza o change folder OpenSpec a partir do modelo (RFC-0002 §2-3). Determinístico: ordenação
 * estável e sem timestamps no corpo, de modo que renderizar a mesma versão produza bytes idênticos
 * (base da idempotência). Função pura.
 */
@Component
public class ChangeFolderRenderer {

    public record RequirementBlock(Requirement requirement, List<Scenario> scenarios) {}

    public LinkedHashMap<String, String> render(Change change, List<RequirementBlock> blocks, List<SpecTask> tasks) {
        String key = changeKey(change);
        String module = moduleName(change);
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("openspec/changes/" + key + "/specs/" + module + "/spec.md", renderSpec(blocks));
        files.put("openspec/changes/" + key + "/tasks.md", renderTasks(tasks));
        return files;
    }

    private String changeKey(Change change) {
        return change.getModule() != null && !change.getModule().isBlank()
                ? change.getModule() : "change-" + change.getId();
    }

    private String moduleName(Change change) {
        return change.getModule() != null && !change.getModule().isBlank()
                ? change.getModule() : "change-" + change.getId();
    }

    private String renderSpec(List<RequirementBlock> blocks) {
        StringBuilder sb = new StringBuilder("# Spec (delta)\n");
        for (RequirementType type : RequirementType.values()) {
            List<RequirementBlock> ofType = blocks.stream()
                    .filter(b -> b.requirement().getType() == type)
                    .sorted(Comparator.comparing(b -> b.requirement().getRequirementId()))
                    .collect(Collectors.toList());
            if (ofType.isEmpty()) {
                continue;
            }
            sb.append("\n## ").append(type.name()).append(" Requirements\n");
            for (RequirementBlock block : ofType) {
                Requirement r = block.requirement();
                sb.append("\n### Requirement: ").append(r.getRequirementId())
                        .append(" — ").append(r.getTitle()).append("\n");
                if (r.getDescription() != null && !r.getDescription().isBlank()) {
                    sb.append(r.getDescription().trim()).append("\n");
                }
                block.scenarios().stream()
                        .sorted(Comparator.comparing(Scenario::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                        .forEach(s -> sb.append("\n#### Scenario: ").append(s.getName()).append("\n")
                                .append("- **WHEN** ").append(s.getWhenCondition()).append("\n")
                                .append("- **THEN** ").append(s.getThenResult()).append("\n"));
            }
        }
        return sb.toString();
    }

    private String renderTasks(List<SpecTask> tasks) {
        StringBuilder sb = new StringBuilder("# Tasks\n\n");
        tasks.stream()
                .sorted(Comparator.comparing(SpecTask::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(t -> {
                    String ref = t.getRequirement() != null ? t.getRequirement().getRequirementId() : "-";
                    sb.append("- [").append(t.getStatus().name()).append("] ")
                            .append(t.getTitle()).append(" (").append(ref).append(")\n");
                });
        return sb.toString();
    }
}
