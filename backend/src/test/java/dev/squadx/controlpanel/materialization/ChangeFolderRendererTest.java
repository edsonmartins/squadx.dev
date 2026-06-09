package dev.squadx.controlpanel.materialization;

import dev.squadx.controlpanel.model.Change;
import dev.squadx.controlpanel.model.Requirement;
import dev.squadx.controlpanel.model.Scenario;
import dev.squadx.controlpanel.model.SpecTask;
import dev.squadx.controlpanel.model.enums.RequirementType;
import dev.squadx.controlpanel.model.enums.SpecTaskStatus;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeFolderRendererTest {

    private final ChangeFolderRenderer renderer = new ChangeFolderRenderer();

    private Change change() {
        Change c = Change.builder().module("auth").build();
        c.setId(5L);
        return c;
    }

    private Requirement requirement(Change c) {
        Requirement r = Requirement.builder().change(c).requirementId("R1")
                .type(RequirementType.ADDED).title("Login").description("Autenticação").build();
        r.setId(9L);
        return r;
    }

    private ChangeFolderRenderer.RequirementBlock block(Requirement r) {
        Scenario s = Scenario.builder().requirement(r).name("login inválido")
                .whenCondition("credenciais erradas").thenResult("rejeita").build();
        s.setId(1L);
        return new ChangeFolderRenderer.RequirementBlock(r, List.of(s));
    }

    private SpecTask task(Change c, Requirement r) {
        SpecTask t = SpecTask.builder().change(c).requirement(r)
                .title("Implementar login").status(SpecTaskStatus.A_FAZER).build();
        t.setId(3L);
        return t;
    }

    @Test
    void rendersDeterministically() {
        Change c = change();
        Requirement r = requirement(c);
        var blocks = List.of(block(r));
        var tasks = List.of(task(c, r));

        LinkedHashMap<String, String> first = renderer.render(c, blocks, tasks);
        LinkedHashMap<String, String> second = renderer.render(c, blocks, tasks);

        assertThat(first).isEqualTo(second); // byte-identical → idempotency base
    }

    @Test
    void specContainsRequirementAndScenario() {
        Change c = change();
        Requirement r = requirement(c);
        var files = renderer.render(c, List.of(block(r)), List.of(task(c, r)));

        String spec = files.get("openspec/changes/auth/specs/auth/spec.md");
        assertThat(spec).contains("## ADDED Requirements");
        assertThat(spec).contains("### Requirement: R1 — Login");
        assertThat(spec).contains("#### Scenario: login inválido");
        assertThat(spec).contains("**WHEN** credenciais erradas");
        assertThat(spec).contains("**THEN** rejeita");
    }

    @Test
    void tasksFileListsTasks() {
        Change c = change();
        Requirement r = requirement(c);
        var files = renderer.render(c, List.of(block(r)), List.of(task(c, r)));

        String tasks = files.get("openspec/changes/auth/tasks.md");
        assertThat(tasks).contains("Implementar login");
        assertThat(tasks).contains("(R1)");
    }
}
