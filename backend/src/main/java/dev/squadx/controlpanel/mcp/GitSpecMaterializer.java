package dev.squadx.controlpanel.mcp;

import dev.squadx.controlpanel.model.Change;
import dev.squadx.controlpanel.model.Requirement;
import dev.squadx.controlpanel.model.Scenario;
import dev.squadx.controlpanel.model.SpecVersion;
import dev.squadx.controlpanel.repository.ChangeRepository;
import dev.squadx.controlpanel.repository.RequirementRepository;
import dev.squadx.controlpanel.repository.ScenarioRepository;
import dev.squadx.controlpanel.repository.SpecVersionRepository;
import dev.squadx.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Deterministic OpenSpec renderer and Git materializer. Disabled by default until repo mirrors are configured. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spec.materialization.enabled", havingValue = "true")
public class GitSpecMaterializer implements SpecMaterializer {
    private final ChangeRepository changeRepository;
    private final RequirementRepository requirementRepository;
    private final ScenarioRepository scenarioRepository;
    private final SpecVersionRepository versionRepository;

    @Value("${intelligence.native.repository-root:${SQUADX_INTELLIGENCE_REPOSITORY_ROOT:/repositories}}")
    private String repositoryRoot;

    @Override
    @Transactional
    public MaterializationResult materialize(Long changeId) {
        Change change = changeRepository.findById(changeId)
                .orElseThrow(() -> new ResourceNotFoundException("Change not found"));
        Path repo = Path.of(repositoryRoot).resolve(String.valueOf(change.getProject().getId())).normalize();
        if (!Files.isDirectory(repo.resolve(".git"))) return MaterializationResult.unavailable("Git mirror not available");

        SpecVersion version = versionRepository.findFirstByChangeIdAndCurrentTrue(changeId).orElseGet(() ->
                versionRepository.save(SpecVersion.builder().change(change).version("1.0.0")
                        .summary("Initial specification").current(true).build()));
        String relative = "openspec/changes/" + changeId + "/spec.md";
        Path target = repo.resolve(relative).normalize();
        if (!target.startsWith(repo)) return MaterializationResult.unavailable("Invalid materialization path");
        String rendered = render(changeId);
        try {
            Files.createDirectories(target.getParent());
            if (Files.exists(target) && !Files.readString(target).equals(rendered)) {
                return MaterializationResult.unavailable("Materialization conflict: existing spec differs");
            }
            if (!Files.exists(target)) Files.writeString(target, rendered, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            run(repo, List.of("git", "add", relative));
            String status = run(repo, List.of("git", "status", "--porcelain", "--", relative));
            String commit;
            if (status.isBlank()) {
                commit = run(repo, List.of("git", "rev-parse", "HEAD")).strip();
            } else {
                run(repo, List.of("git", "commit", "-m", "spec(" + changeId + "): materialize " + version.getVersion()));
                commit = run(repo, List.of("git", "rev-parse", "HEAD")).strip();
            }
            version.setCommitSha(commit);
            versionRepository.save(version);
            return MaterializationResult.of(version.getVersion(), commit);
        } catch (IOException | IllegalStateException e) {
            return MaterializationResult.unavailable(e.getMessage());
        }
    }

    private String render(Long changeId) {
        StringBuilder out = new StringBuilder("# Change ").append(changeId).append("\n\n");
        requirementRepository.findByChangeId(changeId).stream()
                .sorted(Comparator.comparing(Requirement::getRequirementId))
                .forEach(req -> {
                    out.append("## ").append(req.getRequirementId()).append(" — ").append(req.getTitle()).append("\n\n");
                    if (req.getDescription() != null && !req.getDescription().isBlank()) out.append(req.getDescription().strip()).append("\n\n");
                    out.append("**Type:** ").append(req.getType()).append("\n\n");
                    scenarioRepository.findByRequirementId(req.getId()).stream()
                            .sorted(Comparator.comparing(Scenario::getName))
                            .forEach(sc -> out.append("### Scenario: ").append(sc.getName()).append("\n")
                                    .append("- **WHEN:** ").append(sc.getWhenCondition()).append("\n")
                                    .append("- **THEN:** ").append(sc.getThenResult()).append("\n\n"));
                });
        return out.toString();
    }

    private String run(Path repo, List<String> command) {
        try {
            Process process = new ProcessBuilder(command).directory(repo.toFile()).redirectErrorStream(true).start();
            if (!process.waitFor(20, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException("Git command timed out"); }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) throw new IllegalStateException("Git command failed: " + output.strip());
            return output;
        } catch (IOException e) { throw new IllegalStateException("Git unavailable: " + e.getMessage(), e); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Git command interrupted", e); }
    }
}
