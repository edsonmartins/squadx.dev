package dev.squadx.controlpanel.validation;

import dev.squadx.controlpanel.model.SpecTask;
import dev.squadx.controlpanel.repository.SpecTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs the project's native test command in the revision-pinned mirror. */
@Service
@RequiredArgsConstructor
public class NativeTestSuiteExecutor implements TestSuiteExecutor {
    private final SpecTaskRepository taskRepository;

    @Value("${intelligence.native.repository-root:${SQUADX_INTELLIGENCE_REPOSITORY_ROOT:/repositories}}")
    private String repositoryRoot;
    @Value("${pass5.tests.timeout-ms:120000}")
    private int timeoutMs;

    @Override
    public TestExecutionResult execute(Long specTaskId, String revision) {
        if (revision == null || !revision.matches("[0-9a-fA-F]{7,64}")) {
            return TestExecutionResult.failed("SHA da revisão inválido para execução dos testes");
        }
        SpecTask task = taskRepository.findById(specTaskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + specTaskId));
        Path repo = Path.of(repositoryRoot).resolve(String.valueOf(task.getChange().getProject().getId())).normalize();
        List<String> command = commandFor(repo);
        if (command == null) {
            return TestExecutionResult.failed("Nenhum runner de testes suportado foi encontrado no repositório");
        }
        try {
            Process process = new ProcessBuilder(command)
                    .directory(repo.toFile()).redirectErrorStream(true).start();
            if (!process.waitFor(Math.max(timeoutMs, 1000), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return TestExecutionResult.failed("Execução dos testes excedeu o timeout de " + timeoutMs + " ms");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String summary = output.length() > 16_000 ? output.substring(output.length() - 16_000) : output;
            if (process.exitValue() == 0) return TestExecutionResult.passed(summary.strip());
            return TestExecutionResult.failed(summary.strip());
        } catch (IOException e) {
            return TestExecutionResult.failed("Não foi possível iniciar os testes: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TestExecutionResult.failed("Execução dos testes interrompida");
        }
    }

    private List<String> commandFor(Path repo) {
        if (Files.isRegularFile(repo.resolve("mvnw"))) return List.of("./mvnw", "-q", "test");
        if (Files.isRegularFile(repo.resolve("pom.xml"))) return List.of("mvn", "-q", "test");
        if (Files.isRegularFile(repo.resolve("gradlew"))) return List.of("./gradlew", "test");
        if (Files.isRegularFile(repo.resolve("build.gradle")) || Files.isRegularFile(repo.resolve("build.gradle.kts"))) {
            return List.of("gradle", "test");
        }
        if (Files.isRegularFile(repo.resolve("package.json"))) return List.of("npm", "test", "--", "--runInBand");
        if (Files.isRegularFile(repo.resolve("Cargo.toml"))) return List.of("cargo", "test");
        if (Files.isRegularFile(repo.resolve("go.mod"))) return List.of("go", "test", "./...");
        return null;
    }
}
