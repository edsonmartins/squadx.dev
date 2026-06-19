package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.materialization.GitHubCommitGateway;
import dev.squadx.controlpanel.materialization.GitHubReviewClient;
import dev.squadx.controlpanel.model.SpecEvent;
import dev.squadx.controlpanel.model.SpecTask;
import dev.squadx.controlpanel.model.enums.EventSource;
import dev.squadx.controlpanel.model.enums.SpecTaskStatus;
import dev.squadx.controlpanel.model.enums.TaskEventType;
import dev.squadx.controlpanel.repository.SpecEventRepository;
import dev.squadx.controlpanel.repository.SpecTaskRepository;
import dev.squadx.integration.IntegrationConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Varre, de fora para dentro, os comentários de review dos PRs em validação e os ingere como eventos
 * {@code REVIEW_COMMENT} — o caminho confiável quando o git host não consegue alcançar o backend
 * (firewall/cross-border/self-hosted), pois é o backend que sai. Convive com o webhook inbound.
 *
 * <p>Idempotente: usa o id do comentário como {@code sourceRef}, então o {@link SpecEventService}
 * deduplica reentregas automaticamente. Pula os próprios comentários do Pass 5 (marcador) para não
 * criar laço de feedback. Ativo apenas com {@code squadx.git.poll-enabled=true}.
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "squadx.git", name = "poll-enabled", havingValue = "true")
public class PrReviewCommentPoller {

    private final IntegrationConfig.GitConfig git;
    private final RestClient restClient;
    private final SpecTaskRepository specTaskRepository;
    private final SpecEventRepository specEventRepository;
    private final SpecEventService specEventService;

    public PrReviewCommentPoller(IntegrationConfig config, RestClient.Builder builder,
                                 SpecTaskRepository specTaskRepository,
                                 SpecEventRepository specEventRepository,
                                 SpecEventService specEventService) {
        this.git = config.getGit();
        this.restClient = builder.baseUrl(git.getApiUrl()).build();
        this.specTaskRepository = specTaskRepository;
        this.specEventRepository = specEventRepository;
        this.specEventService = specEventService;
    }

    @Scheduled(fixedDelayString = "${squadx.git.poll-interval-ms:90000}")
    public void poll() {
        if (!git.isEnabled() || isBlank(git.getToken())) {
            return;
        }
        for (SpecTask task : specTaskRepository.findByStatus(SpecTaskStatus.EM_VALIDACAO)) {
            try {
                pollTask(task);
            } catch (Exception e) {
                log.warn("PR comment poll failed for task {}: {}", task.getId(), e.getMessage());
            }
        }
    }

    private void pollTask(SpecTask task) {
        String repositoryUrl = task.getChange() != null && task.getChange().getProject() != null
                ? task.getChange().getProject().getRepositoryUrl() : null;
        String prNumber = latestPrNumber(task.getId());
        if (repositoryUrl == null || prNumber == null) {
            return;
        }
        String[] ownerRepo = GitHubCommitGateway.parseOwnerRepo(repositoryUrl);
        if (ownerRepo == null) {
            return;
        }
        String repo = "/repos/" + ownerRepo[0] + "/" + ownerRepo[1];
        ingest(task.getId(), repo + "/pulls/" + prNumber + "/comments", "review-comment-");
        ingest(task.getId(), repo + "/issues/" + prNumber + "/comments", "issue-comment-");
    }

    /** Último número de PR conhecido para a tarefa, a partir do evento PR_OPENED ({@code pr-<n>}). */
    private String latestPrNumber(Long taskId) {
        String prNumber = null;
        for (SpecEvent e : specEventRepository.findBySpecTaskIdOrderByOccurredAtAscIdAsc(taskId)) {
            if (e.getType() == TaskEventType.PR_OPENED && e.getSourceRef() != null
                    && e.getSourceRef().startsWith("pr-")) {
                prNumber = e.getSourceRef().substring("pr-".length());
            }
        }
        return prNumber;
    }

    @SuppressWarnings("unchecked")
    private void ingest(Long taskId, String path, String refPrefix) {
        List<Map<String, Object>> comments = restClient.get().uri(path)
                .headers(this::authHeaders).retrieve().body(List.class);
        if (comments == null) {
            return;
        }
        for (Map<String, Object> c : comments) {
            String body = asString(c.get("body"));
            // Skip our own Pass 5 review comments — never ingest what we posted (no feedback loop).
            if (body != null && body.contains(GitHubReviewClient.MARKER_PREFIX)) {
                continue;
            }
            Object id = c.get("id");
            if (id == null) {
                continue;
            }
            String author = c.get("user") instanceof Map<?, ?> u ? asString(u.get("login")) : null;
            String payload = (author != null ? author + ": " : "") + truncate(body, 500);
            specEventService.record(taskId, TaskEventType.REVIEW_COMMENT, EventSource.GIT,
                    refPrefix + id, payload, parseInstant(c.get("created_at")));
        }
    }

    private void authHeaders(HttpHeaders headers) {
        headers.setBearerAuth(git.getToken());
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");
    }

    private static Instant parseInstant(Object value) {
        try {
            return value != null ? Instant.parse(String.valueOf(value)) : Instant.now();
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private static String truncate(String text, int limit) {
        if (text == null) {
            return "";
        }
        return text.length() <= limit ? text : text.substring(0, limit - 3) + "...";
    }

    private static String asString(Object o) {
        return o != null ? String.valueOf(o) : null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
