package dev.squadx.controlpanel.validation;

import dev.squadx.controlpanel.model.SpecTask;
import dev.squadx.controlpanel.repository.SpecTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Resolves PR diffs from the local, revision-pinned repository mirror. */
@Service
@RequiredArgsConstructor
public class NativeGitDiffProvider implements GitDiffProvider {
    private final SpecTaskRepository taskRepository;
    @Value("${intelligence.native.repository-root:${SQUADX_INTELLIGENCE_REPOSITORY_ROOT:/repositories}}")
    private String repositoryRoot;

    @Override
    public String diff(Long specTaskId, String revision) {
        if (revision == null || !revision.matches("[0-9a-fA-F]{7,64}")) return null;
        SpecTask task = taskRepository.findById(specTaskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + specTaskId));
        Path repo = Path.of(repositoryRoot).resolve(String.valueOf(task.getChange().getProject().getId())).normalize();
        try {
            Process process = new ProcessBuilder(List.of("git", "diff", revision + "^", revision))
                    .directory(repo.toFile()).redirectErrorStream(true).start();
            if (!process.waitFor(Duration.ofSeconds(20).toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("Git diff timed out");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) throw new IllegalStateException("Git diff failed: " + output.strip());
            if (output.length() > 2_000_000) throw new IllegalStateException("Git diff exceeds conformance limit");
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to resolve Git diff", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Git diff interrupted", e);
        }
    }
}
