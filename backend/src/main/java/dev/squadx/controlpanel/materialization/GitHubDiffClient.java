package dev.squadx.controlpanel.materialization;

import dev.squadx.integration.IntegrationConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Busca o diff unificado de um PR no GitHub (Accept {@code application/vnd.github.v3.diff}), reusando
 * o token de {@code squadx.git}. Retorna {@code null} se não configurado / repositório não suportado.
 */
@Component
@Slf4j
public class GitHubDiffClient {

    private final IntegrationConfig.GitConfig git;
    private final RestClient restClient;

    public GitHubDiffClient(IntegrationConfig config, RestClient.Builder builder) {
        this.git = config.getGit();
        this.restClient = builder.baseUrl(git.getApiUrl()).build();
    }

    public String fetchPullRequestDiff(String repositoryUrl, String prNumber) {
        if (git.getToken() == null || git.getToken().isBlank() || repositoryUrl == null || prNumber == null) {
            return null;
        }
        String[] ownerRepo = GitHubCommitGateway.parseOwnerRepo(repositoryUrl);
        if (ownerRepo == null) {
            return null;
        }
        try {
            return restClient.get()
                    .uri("/repos/" + ownerRepo[0] + "/" + ownerRepo[1] + "/pulls/" + prNumber)
                    .headers(h -> {
                        h.setBearerAuth(git.getToken());
                        h.set("Accept", "application/vnd.github.v3.diff");
                        h.set("X-GitHub-Api-Version", "2022-11-28");
                    })
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn("Failed to fetch PR diff for {} #{}: {}", repositoryUrl, prNumber, e.getMessage());
            return null;
        }
    }
}
