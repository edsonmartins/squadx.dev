package dev.squadx.controlpanel.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskDecisionParserTest {

    private final TaskDecisionParser parser = new TaskDecisionParser();

    private static final String ADR = """
            # ADR-0012 — isolamento da fase interna

            ---
            id: ADR-0012
            status: Proposto
            data: 2026-08-10
            ---

            Contexto...

            ## Tarefas derivadas

            | # | Tarefa | Prioridade |
            |---|---|--------|
            | T-0012-1 | Definir isolamento | P0 |
            | T-0012-2 | Implementar | P1 |
            """;

    @Test
    void parsesTasksFromDecision() {
        List<CandidateTask> tasks = parser.parse(ADR, "docs/adr/ADR-0012-isolamento.md", "ADR");

        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0).id()).isEqualTo("T-0012-1");
        assertThat(tasks.get(0).title()).isEqualTo("Definir isolamento");
        assertThat(tasks.get(0).priority()).isEqualTo("P0");
        assertThat(tasks.get(0).sourceKind()).isEqualTo("ADR");
        assertThat(tasks.get(0).sourceRef()).isEqualTo("docs/adr/ADR-0012-isolamento.md#T-0012-1");
    }

    @Test
    void returnsEmptyWhenNoTasksSection() {
        String doc = "# ADR-X\n\n---\nid: ADR-X\nstatus: Aceito\n---\n\nApenas contexto.";
        assertThat(parser.parse(doc, "x.md", "ADR")).isEmpty();
    }

    @Test
    void ignoresTableHeaderAndSeparator() {
        // só a linha de tarefa deve ser capturada, não header/separador
        List<CandidateTask> tasks = parser.parse(ADR, "a.md", "ADR");
        assertThat(tasks).extracting(CandidateTask::id)
                .containsExactly("T-0012-1", "T-0012-2");
        assertThat(tasks).extracting(CandidateTask::id)
                .doesNotContain("#", "Tarefa");
    }

    @Test
    void malformedFrontMatterFailsLoudly() {
        // começa com '---' mas não fecha → erro, nunca silêncio
        String broken = "---\nid: ADR-X\n\nsem fechamento";
        assertThatThrownBy(() -> parser.parse(broken, "broken.md", "ADR"))
                .isInstanceOf(TaskDecisionParser.ParseException.class)
                .hasMessageContaining("Front-matter malformado");
    }

    @Test
    void toleratesMissingFrontMatterIfNoTasks() {
        // sem '---' no início, sem seção de tarefas → vazio, sem erro
        assertThat(parser.parse("apenas texto", "x.md", "RFC")).isEmpty();
    }
}
