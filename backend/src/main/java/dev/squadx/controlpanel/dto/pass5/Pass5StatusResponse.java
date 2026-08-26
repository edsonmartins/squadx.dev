package dev.squadx.controlpanel.dto.pass5;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.controlpanel.model.enums.Pass5Result;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Estado do Pass 5 de uma tarefa: último desfecho + mapa de cobertura cenário↔teste (R6). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pass5StatusResponse {

    @JsonProperty("spec_task_id")
    private Long specTaskId;

    /** Último desfecho (null se nunca validada). */
    private Pass5Result outcome;

    private String critique;

    @JsonProperty("coverage_total")
    private int coverageTotal;

    @JsonProperty("coverage_covered")
    private int coverageCovered;

    private List<ScenarioCoverage> scenarios;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScenarioCoverage {
        private Long id;
        private String name;
        private boolean covered;
    }
}

