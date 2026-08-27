package dev.squadx.controlpanel.parser;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser do corpus de decisão (ADR/RFC/change) → tarefas candidatas.
 *
 * <p>Gramática mínima do RFC-0007 (T-0010-1/T-0010-2): front-matter YAML com
 * {@code id}, {@code status}, {@code data} fechado por {@code ---}, e uma seção
 * {@code ## Tarefas derivadas} com tabela de linhas {@code | id | título | prioridade |}.
 * Front-matter inválido falha ruidosamente — nunca é ignorado em silêncio.</p>
 */
@Component
public class TaskDecisionParser {

    private static final Pattern FRONT_MATTER = Pattern.compile(
            "^---$\\s*\n(.*?)\n---", Pattern.DOTALL | Pattern.MULTILINE);

    private static final Pattern ID = Pattern.compile("^\\s*id\\s*:\\s*(.+?)\\s*$", Pattern.MULTILINE);
    private static final Pattern STATUS = Pattern.compile("^\\s*status\\s*:\\s*(.+?)\\s*$", Pattern.MULTILINE);

    private static final Pattern TASKS_SECTION = Pattern.compile(
            "##\\s+Tarefas derivadas\\s*\n(.*?)(?=\\n##\\s|\\z)", Pattern.DOTALL);
    private static final Pattern TABLE_ROW = Pattern.compile(
            "^\\|\\s*([A-Za-z0-9._/-]+)\\s*\\|\\s*([^|]*?)\\s*\\|(?:\\s*([^|]*?))?\\s*\\|?\\s*$");

    /**
     * Parseia um documento de decisão e devolve as tarefas candidatas.
     *
     * @param content  conteúdo markdown do arquivo
     * @param path     caminho do arquivo (ex.: "docs/rfc/RFC-0007-x.md")
     * @param sourceKind tipo de decisão (ADR | RFC | CHANGE)
     * @return lista de tarefas candidatas (vazia se não há seção de tarefas)
     * @throws ParseException se o front-matter for inválido
     */
    public List<CandidateTask> parse(String content, String path, String sourceKind) {
        String id = "";
        if (content != null && content.startsWith("---")) {
            Matcher fm = FRONT_MATTER.matcher(content);
            if (fm.find()) {
                String body = fm.group(1);
                Matcher mid = ID.matcher(body);
                if (mid.find()) {
                    id = mid.group(1).trim();
                }
            } else {
                // começa com '---' mas não fecha → front-matter malformado
                throw new ParseException("Front-matter malformado (abre com --- mas não fecha): " + path);
            }
        }

        Matcher sec = TASKS_SECTION.matcher(content == null ? "" : content);
        if (!sec.find()) {
            return List.of();
        }
        String block = sec.group(1);

        List<CandidateTask> out = new ArrayList<>();
        for (String line : block.split("\n")) {
            String trimmed = line.trim();
            // ignora cabeçalho da tabela e separadores
            if (trimmed.startsWith("|") && !trimmed.matches("\\|\\s*:?-+:?\\s*\\|.*")) {
                Matcher row = TABLE_ROW.matcher(trimmed);
                if (row.matches()) {
                    out.add(new CandidateTask(
                            row.group(1).trim(),
                            row.group(2).trim(),
                            row.group(3) == null ? "" : row.group(3).trim(),
                            sourceKind,
                            path + "#" + row.group(1).trim()));
                }
            }
        }
        // id do front-matter usado como fallback de âncora? Não: a âncora é path#taskId (RFC-0007 §2).
        return out;
    }

    /** Exceção de parse — falha ruidosa, nunca silenciosa (ADR-0010 §Riscos). */
    public static final class ParseException extends RuntimeException {
        public ParseException(String message) {
            super(message);
        }
    }
}
