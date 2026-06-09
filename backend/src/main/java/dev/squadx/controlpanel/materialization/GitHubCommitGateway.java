package dev.squadx.controlpanel.materialization;

import dev.squadx.integration.IntegrationConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Materializa a spec no GitHub via Git Data API (blobs → tree → commit → update ref), numa branch
 * da mudança ({@code <prefix><changeKey>}) criada a partir da branch base do projeto (RFC-0002).
 * Faz no-op gracioso quando desabilitado/sem token/sem repositório suportado, preservando o
 * comportamento "pendente". Conflito (head divergiu) é sinalizado (R5).
 */
@Component
@Slf4j
public class GitHubCommitGateway implements GitCommitGateway {

    private static final Pattern GITHUB_URL =
            Pattern.compile("github\\.com[/:]([^/]+)/([^/.]+)(?:\\.git)?/?$");

    private final IntegrationConfig.GitConfig git;
    private final RestClient restClient;

    public GitHubCommitGateway(IntegrationConfig config, RestClient.Builder builder) {
        this.git = config.getGit();
        this.restClient = builder.baseUrl(git.getApiUrl()).build();
    }

    @Override
    public CommitResult commit(GitTarget target, Map<String, String> files, String message) {
        if (!git.isEnabled() || isBlank(git.getToken()) || target.repositoryUrl() == null) {
            return CommitResult.skipped("git materialization not configured");
        }
        String[] ownerRepo = parseOwnerRepo(target.repositoryUrl());
        if (ownerRepo == null) {
            return CommitResult.skipped("unsupported repository URL: " + target.repositoryUrl());
        }
        String base = "/repos/" + ownerRepo[0] + "/" + ownerRepo[1] + "/git";
        String branch = git.getBranchPrefix() + target.changeKey();

        try {
            String headSha = ensureBranch(base, branch, target.baseBranch());
            String baseTree = sha(get(base + "/commits/" + headSha).get("tree"));

            List<Map<String, Object>> treeEntries = new ArrayList<>();
            for (Map.Entry<String, String> file : files.entrySet()) {
                String blobSha = sha(post(base + "/blobs",
                        Map.of("content", file.getValue(), "encoding", "utf-8")));
                treeEntries.add(Map.of("path", file.getKey(), "mode", "100644", "type", "blob", "sha", blobSha));
            }
            String newTree = sha(post(base + "/trees", Map.of("base_tree", baseTree, "tree", treeEntries)));
            String commitSha = sha(post(base + "/commits",
                    Map.of("message", message, "tree", newTree, "parents", List.of(headSha))));

            updateRef(base, branch, commitSha);
            return CommitResult.committed(commitSha);
        } catch (HttpClientErrorException.UnprocessableEntity | HttpClientErrorException.Conflict ex) {
            log.warn("Materialization conflict for {}@{}: {}", target.repositoryUrl(), branch, ex.getMessage());
            return CommitResult.conflict("materialization conflict (branch moved or invalid update)");
        } catch (Exception ex) {
            log.warn("GitHub materialization failed for {}: {}", target.repositoryUrl(), ex.getMessage());
            return CommitResult.skipped("git error: " + ex.getMessage());
        }
    }

    /** Garante a branch da mudança; cria a partir da base se ausente. Retorna o sha do head. */
    private String ensureBranch(String base, String branch, String baseBranch) {
        try {
            return sha(get(base + "/ref/heads/" + branch).get("object"));
        } catch (HttpClientErrorException.NotFound notFound) {
            String baseSha = sha(get(base + "/ref/heads/" + baseBranch).get("object"));
            post(base + "/refs", Map.of("ref", "refs/heads/" + branch, "sha", baseSha));
            return baseSha;
        }
    }

    private void updateRef(String base, String branch, String commitSha) {
        restClient.patch()
                .uri(base + "/refs/heads/" + branch)
                .headers(this::authHeaders)
                .body(Map.of("sha", commitSha, "force", false))
                .retrieve()
                .toBodilessEntity();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String path) {
        return restClient.get().uri(path).headers(this::authHeaders).retrieve().body(Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Object body) {
        return restClient.post().uri(path).headers(this::authHeaders).body(body).retrieve().body(Map.class);
    }

    private void authHeaders(HttpHeaders headers) {
        headers.setBearerAuth(git.getToken());
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");
    }

    /** Extrai o {@code sha} de um objeto da resposta (ref.object, commit.tree, blob, tree, commit). */
    @SuppressWarnings("unchecked")
    private String sha(Object node) {
        if (node instanceof Map<?, ?> map && map.get("sha") != null) {
            return String.valueOf(map.get("sha"));
        }
        throw new IllegalStateException("missing sha in GitHub response");
    }

    static String[] parseOwnerRepo(String repositoryUrl) {
        Matcher m = GITHUB_URL.matcher(repositoryUrl.trim());
        return m.find() ? new String[]{m.group(1), m.group(2)} : null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
