package dev.squadx.controlpanel.materialization;

import dev.squadx.controlpanel.mcp.SpecMaterializer;
import dev.squadx.controlpanel.model.Change;
import dev.squadx.controlpanel.model.SpecVersion;
import dev.squadx.controlpanel.repository.*;
import dev.squadx.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Materializa a versão corrente da spec de uma mudança no Git (ADR-0001, RFC-0002 §3). Render
 * determinístico + idempotência por content-hash; o commit real é delegado ao {@link GitCommitGateway}.
 */
@Service
@RequiredArgsConstructor
public class DefaultSpecMaterializer implements SpecMaterializer {

    private final ChangeRepository changeRepository;
    private final RequirementRepository requirementRepository;
    private final ScenarioRepository scenarioRepository;
    private final SpecTaskRepository specTaskRepository;
    private final SpecVersionRepository specVersionRepository;
    private final ChangeFolderRenderer renderer;
    private final GitCommitGateway gitCommitGateway;

    @Override
    @Transactional
    public MaterializationResult materialize(Long changeId) {
        Change change = changeRepository.findById(changeId)
                .orElseThrow(() -> new ResourceNotFoundException("Change not found"));
        SpecVersion version = specVersionRepository.findByChangeIdAndCurrentTrue(changeId)
                .orElseGet(() -> createAutoV1(change));

        List<ChangeFolderRenderer.RequirementBlock> blocks = requirementRepository.findByChangeId(changeId).stream()
                .map(r -> new ChangeFolderRenderer.RequirementBlock(r, scenarioRepository.findByRequirementId(r.getId())))
                .collect(Collectors.toList());
        LinkedHashMap<String, String> files =
                renderer.render(change, blocks, specTaskRepository.findByChangeId(changeId));
        String hash = contentHash(files);
        String label = "v" + version.getVersion();

        // Idempotência: mesma versão já materializada com conteúdo idêntico → no-op.
        if (version.getCommit() != null && hash.equals(version.getContentHash())) {
            return MaterializationResult.of(label, version.getCommit());
        }

        String key = changeKey(change);
        GitCommitGateway.GitTarget target = new GitCommitGateway.GitTarget(
                change.getProject().getRepositoryUrl(), change.getProject().getDefaultBranch(), key);
        GitCommitGateway.CommitResult result = gitCommitGateway.commit(
                target, files, "spec(" + key + "): materialize " + label);

        version.setContentHash(hash);
        if (result.sha() != null) {
            version.setCommit(result.sha());
            specVersionRepository.save(version);
            return MaterializationResult.of(label, result.sha());
        }
        specVersionRepository.save(version);
        if (result.conflict()) {
            return MaterializationResult.unavailable("Conflito de materialização: " + result.message());
        }
        return new MaterializationResult(false, label, null,
                "Rendered " + files.size() + " file(s); git commit pendente (" + result.message() + ")");
    }

    private SpecVersion createAutoV1(Change change) {
        return specVersionRepository.save(SpecVersion.builder()
                .change(change).version(1).current(true).summary("auto").build());
    }

    private String changeKey(Change change) {
        return change.getModule() != null && !change.getModule().isBlank()
                ? change.getModule() : "change-" + change.getId();
    }

    private String contentHash(Map<String, String> files) {
        // Ordena por path para um hash estável (idempotência).
        TreeMap<String, String> sorted = new TreeMap<>(files);
        StringBuilder sb = new StringBuilder();
        sorted.forEach((path, content) -> sb.append(path).append('\n').append(content).append('\n'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
