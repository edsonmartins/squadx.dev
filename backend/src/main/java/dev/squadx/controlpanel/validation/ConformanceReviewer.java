package dev.squadx.controlpanel.validation;

import java.util.List;

/**
 * Revisor de conformidade comportamental plugável (RFC-0004 §7). O default é o
 * {@link NoopConformanceReviewer}; o cliente Pullwise real é uma integração posterior que
 * implementa esta interface, isolando o Control Panel do fornecedor.
 */
public interface ConformanceReviewer {

    ConformanceVerdict review(ConformanceRequest request);

    /** Cenário simplificado para a revisão (sem expor entidades JPA). */
    record ScenarioRef(String name, String when, String then) {}

    record ConformanceRequest(Long specTaskId, String prSha, List<ScenarioRef> scenarios, String gitDiff) {
        public ConformanceRequest(Long specTaskId, String prSha, List<ScenarioRef> scenarios) {
            this(specTaskId, prSha, scenarios, null);
        }
    }
}
