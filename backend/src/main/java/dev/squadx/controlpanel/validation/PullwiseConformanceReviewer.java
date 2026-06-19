package dev.squadx.controlpanel.validation;

import dev.squadx.controlpanel.materialization.GitHubDiffClient;
import dev.squadx.controlpanel.materialization.GitHubReviewClient;
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
    private final GitHubReviewClient reviewClient;

    public PullwiseConformanceReviewer(IntegrationConfig config, RestClient.Builder builder,
                                       GitHubDiffClient diffClient, GitHubReviewClient reviewClient) {
        this.pullwise = config.getPullwise();
        this.restClient = builder.baseUrl(pullwise.getUrl()).build();
        this.diffClient = diffClient;
        this.reviewClient = reviewClient;
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
            String critique = buildCritique(response);
            publishReview(request, critique, response); // efeito colateral best-effort; não afeta o verdict
            return ConformanceVerdict.diverges(critique);
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

    /** Publica os achados divergentes como uma review idempotente no PR (Pass 5 → feedback acionável). */
    private void publishReview(ConformanceRequest request, String summary, Map<?, ?> response) {
        List<GitHubReviewClient.ReviewFinding> findings = toFindings(response);
        if (!findings.isEmpty()) {
            reviewClient.publishConformanceReview(
                    request.repositoryUrl(), request.prNumber(), request.prSha(), summary, findings);
        }
    }

    private List<GitHubReviewClient.ReviewFinding> toFindings(Map<?, ?> response) {
        if (!(response.get("criteria") instanceof List<?> list)) {
            return List.of();
        }
        List<GitHubReviewClient.ReviewFinding> findings = new java.util.ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> c && Boolean.FALSE.equals(c.get("ok"))) {
                findings.add(new GitHubReviewClient.ReviewFinding(
                        asString(c.get("name")),
                        asString(c.get("note")),
                        asString(c.get("file") != null ? c.get("file") : c.get("path")),
                        asInt(c.get("line"))));
            }
        }
        return findings;
    }

    private static String asString(Object o) {
        return o != null ? String.valueOf(o) : null;
    }

    private static Integer asInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return o != null ? Integer.parseInt(String.valueOf(o)) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
