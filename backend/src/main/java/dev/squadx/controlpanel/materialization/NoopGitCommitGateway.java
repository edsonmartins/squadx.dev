package dev.squadx.controlpanel.materialization;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Gateway padrão: não comita (sem credenciais/working-tree configurados). A spec é renderizada e
 * versionada, mas o commit fica pendente até a integração Git real.
 */
@Service
@Slf4j
public class NoopGitCommitGateway implements GitCommitGateway {

    @Override
    public CommitResult commit(String changeKey, Map<String, String> files, String message) {
        log.info("Git commit gateway not configured; {} file(s) rendered for '{}' but not committed",
                files.size(), changeKey);
        return new CommitResult(null);
    }
}
