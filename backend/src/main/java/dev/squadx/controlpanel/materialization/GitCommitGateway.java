package dev.squadx.controlpanel.materialization;

import java.util.Map;

/**
 * Porta para o commit da spec materializada no Git (RFC-0002). A implementação padrão é
 * {@link GitHubCommitGateway} (GitHub Git Data API), que faz no-op gracioso quando não configurada.
 */
public interface GitCommitGateway {

    CommitResult commit(GitTarget target, Map<String, String> files, String message);

    /**
     * Garante um PR aberto da branch da mudança para a base (idempotente: devolve o existente se
     * já houver). {@code url == null} quando não configurado / não suportado / erro.
     */
    PullRequestResult openPullRequest(GitTarget target, String title, String body);

    record PullRequestResult(String url) {

        public static PullRequestResult none() {
            return new PullRequestResult(null);
        }
    }

    /** Alvo do commit: repositório do projeto, branch base e a chave da mudança (vira branch). */
    record GitTarget(String repositoryUrl, String baseBranch, String changeKey) {}

    /**
     * Resultado: {@code sha != null} = commit feito; {@code conflict} = head divergiu (R5);
     * caso contrário (sha null, sem conflito) = não materializado (não configurado / erro).
     */
    record CommitResult(String sha, boolean conflict, String message) {

        public static CommitResult committed(String sha) {
            return new CommitResult(sha, false, null);
        }

        public static CommitResult conflict(String message) {
            return new CommitResult(null, true, message);
        }

        public static CommitResult skipped(String message) {
            return new CommitResult(null, false, message);
        }
    }
}
