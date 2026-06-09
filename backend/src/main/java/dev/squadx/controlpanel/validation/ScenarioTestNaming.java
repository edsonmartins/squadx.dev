package dev.squadx.controlpanel.validation;

import java.text.Normalizer;

/**
 * Convenção de nome rastreável cenário↔teste (ADR-0005, RFC-0004 §2): um método de teste por
 * cenário, nomeado {@code <requirementRef>_<slug-do-cenário>} (ex.: {@code R1_login_invalido}).
 * Fonte única usada pelo scaffold ({@code scaffold_tests}) e pelo scan de cobertura do Pass 5.
 */
public final class ScenarioTestNaming {

    private ScenarioTestNaming() {
    }

    public static String methodName(String requirementRef, String scenarioName) {
        return requirementRef + "_" + slug(scenarioName);
    }

    public static String slug(String name) {
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");                          // strip diacritics (á -> a)
        String s = normalized.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        return s.isEmpty() ? "scenario" : s;
    }
}
