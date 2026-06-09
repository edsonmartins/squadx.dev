package dev.squadx.controlpanel.materialization;

import java.util.Map;

/**
 * Porta para o commit/push real da spec materializada no Git (RFC-0002). A implementação real
 * (working-tree + credenciais + detecção de conflito — R5) é integração externa; o default é
 * {@link NoopGitCommitGateway}.
 */
public interface GitCommitGateway {

    CommitResult commit(String changeKey, Map<String, String> files, String message);

    /** {@code sha == null} indica que nada foi comitado (ex.: gateway não configurado). */
    record CommitResult(String sha) {}
}
