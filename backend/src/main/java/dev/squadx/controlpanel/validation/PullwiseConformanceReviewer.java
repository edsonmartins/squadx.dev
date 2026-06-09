package dev.squadx.controlpanel.validation;

import dev.squadx.controlpanel.materialization.GitHubDiffClient;
import dev.squadx.integration.IntegrationConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Revisor de conformidade do Pass 5 via Pullwise (RFC-0004 §7). Busca o diff do PR (GitHub) e o
 * envia, com os cenários WHEN/THEN, ao endpoint stateless de conformidade do Pullwise. Ativo apenas
 * quando {@code squadx.pullwise.enabled=true} (é {@code @Primary} sobre o {@link NoopConformanceReviewer});
 * degrada para {@code ok()} quando falta contexto (sem repo/PR/diff) ou em erro — a cobertura
 * cenário↔teste é o gate duro.
 */
@Slf4j
@Service
@Primary
@ConditionalOnProperty(prefix = "squadx.pullwise", name = "enabled", havingValue = "true")
public class PullwiseConformanceReviewer implements ConformanceReviewer {

    private final IntegrationConfig.PullwiseConfig pullwise;
    private final RestClient restClient;
    private final GitHubDiffClient diffClient;

    public PullwiseConformanceReviewer(IntegrationConfig config, RestClient.Builder builder,
                                       GitHubDiffClient diffClient) {
        this.pullwise = config.getPullwise();
        this.restClient = builder.baseUrl(pullwise.getUrl()).build();
        this.diffClient = diffClient;
    }

    @Override
    public ConformanceVerdict review(ConformanceRequest request) {
        if (request.repositoryUrl() == null || request.prNumber() == null) {
            return ConformanceVerdict.ok(); // sem PR para revisar (ex.: revalidação sob demanda)
        }
        String diff = diffClient.fetchPullRequestDiff(request.repositoryUrl(), request.prNumber());
        if (diff == null || diff.isBlank()) {
            return ConformanceVerdict.ok();
        }
        try {
            Map<String, Object> body = Map.of(
                    "gitDiff", diff,
                    "criteria", request.scenarios().stream()
                            .map(s -> Map.of("name", s.name(), "when", s.when(), "then", s.then()))
                            .collect(Collectors.toList()));

            Map<?, ?> response = restClient.post()
                    .uri("/api/conformance/review")
                    .headers(h -> {
                        if (pullwise.getApiKey() != null && !pullwise.getApiKey().isBlank()) {
                            h.set("X-API-Key", pullwise.getApiKey());
                        }
                    })
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response == null || !Boolean.TRUE.equals(response.get("diverges"))) {
                return ConformanceVerdict.ok();
            }
            return ConformanceVerdict.diverges(buildCritique(response));
        } catch (Exception e) {
            log.warn("Pullwise conformance review failed for task {}: {}", request.specTaskId(), e.getMessage());
            return ConformanceVerdict.ok();
        }
    }

    @SuppressWarnings("unchecked")
    private String buildCritique(Map<?, ?> response) {
        StringBuilder sb = new StringBuilder();
        Object summary = response.get("summary");
        if (summary != null) {
            sb.append(summary);
        }
        Object criteria = response.get("criteria");
        if (criteria instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> c && Boolean.FALSE.equals(c.get("ok"))) {
                    sb.append("\n- ").append(c.get("name")).append(": ").append(c.get("note"));
                }
            }
        }
        return sb.toString().isBlank() ? "conformance diverged" : sb.toString();
    }
}
