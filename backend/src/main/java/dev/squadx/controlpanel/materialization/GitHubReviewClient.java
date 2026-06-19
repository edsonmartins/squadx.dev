package dev.squadx.controlpanel.materialization;

import dev.squadx.integration.IntegrationConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Publica a revisão de conformidade do Pass 5 no PR do GitHub como UMA review (sumário + comentários),
 * de forma idempotente: cada achado carrega um marcador HTML e os já presentes no PR são pulados, então
 * reexecutar o Pass 5 nunca duplica comentários. Ancora um achado à linha quando ele traz {@code path}+
 * {@code line}; senão dobra no corpo do sumário (cap de {@value #MAX_INLINE} inline). Best-effort: reusa o
 * token de {@code squadx.git} e NUNCA lança — o gate é a cobertura/verdict, não a publicação.
 */
@Component
@Slf4j
public class GitHubReviewClient {

    public static final String MARKER_PREFIX = "<!-- squadx-pass5:";
    private static final int MAX_INLINE = 10;

    private final IntegrationConfig.GitConfig git;
    private final RestClient restClient;

    public GitHubReviewClient(IntegrationConfig config, RestClient.Builder builder) {
        this.git = config.getGit();
        this.restClient = builder.baseUrl(git.getApiUrl()).build();
    }

    /** Um achado de conformidade. Inline quando traz {@code path}+{@code line}; senão vai ao sumário. */
    public record ReviewFinding(String title, String detail, String path, Integer line) {
        boolean inlineable() {
            return path != null && !path.isBlank() && line != null && line > 0;
        }

        String marker() {
            String key = (path == null ? "" : path) + ":" + (line == null ? "" : line) + ":" + (title == null ? "" : title);
            return MARKER_PREFIX + sha1(key) + " -->";
        }
    }

    /**
     * Posta a review no PR. No-op gracioso quando git não está configurado, faltam repo/PR, ou não há
     * achado novo (todos os marcadores já existem). Cada comentário começa pelo seu marcador (dedup).
     */
    public void publishConformanceReview(String repositoryUrl, String prNumber, String headSha,
                                         String summary, List<ReviewFinding> findings) {
        if (!git.isEnabled() || isBlank(git.getToken()) || isBlank(repositoryUrl) || isBlank(prNumber)
                || findings == null || findings.isEmpty()) {
            return;
        }
        String[] ownerRepo = GitHubCommitGateway.parseOwnerRepo(repositoryUrl);
        if (ownerRepo == null) {
            return;
        }
        String base = "/repos/" + ownerRepo[0] + "/" + ownerRepo[1] + "/pulls/" + prNumber;
        try {
            List<String> existing = existingCommentBodies(base);
            List<ReviewFinding> fresh = findings.stream()
                    .filter(f -> existing.stream().noneMatch(b -> b.contains(f.marker())))
                    .toList();
            if (fresh.isEmpty()) {
                log.debug("Pass5 review for {} #{}: nothing new to post", repositoryUrl, prNumber);
                return;
            }

            List<Map<String, Object>> inline = new ArrayList<>();
            StringBuilder body = new StringBuilder(summary == null ? "" : summary);
            for (ReviewFinding f : fresh) {
                String text = f.marker() + "\n" + renderFinding(f);
                if (f.inlineable() && inline.size() < MAX_INLINE) {
                    inline.add(Map.of("path", f.path(), "line", f.line(), "side", "RIGHT", "body", text));
                } else {
                    body.append("\n\n").append(text);
                }
            }

            Map<String, Object> review = new HashMap<>();
            review.put("event", "COMMENT"); // nunca APPROVE: aprovação é do gate/humano, não do reviewer
            if (!isBlank(headSha)) {
                review.put("commit_id", headSha);
            }
            review.put("body", body.toString());
            if (!inline.isEmpty()) {
                review.put("comments", inline);
            }

            restClient.post().uri(base + "/reviews").headers(this::authHeaders).body(review)
                    .retrieve().toBodilessEntity();
            log.info("Posted Pass5 conformance review to {} #{} ({} new finding(s))",
                    repositoryUrl, prNumber, fresh.size());
        } catch (Exception e) {
            log.warn("Failed to publish Pass5 review to {} #{}: {}", repositoryUrl, prNumber, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> existingCommentBodies(String base) {
        try {
            List<Map<String, Object>> comments = restClient.get().uri(base + "/comments")
                    .headers(this::authHeaders).retrieve().body(List.class);
            if (comments == null) {
                return List.of();
            }
            return comments.stream()
                    .map(c -> c.get("body"))
                    .filter(java.util.Objects::nonNull)
                    .map(String::valueOf)
                    .toList();
        } catch (Exception e) {
            log.debug("Could not fetch existing PR comments for dedup: {}", e.getMessage());
            return List.of();
        }
    }

    private String renderFinding(ReviewFinding f) {
        StringBuilder sb = new StringBuilder("**").append(f.title() == null ? "Conformance finding" : f.title()).append("**");
        if (f.detail() != null && !f.detail().isBlank()) {
            sb.append("\n").append(f.detail());
        }
        return sb.toString();
    }

    private void authHeaders(HttpHeaders headers) {
        headers.setBearerAuth(git.getToken());
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");
    }

    private static String sha1(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 12);
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
