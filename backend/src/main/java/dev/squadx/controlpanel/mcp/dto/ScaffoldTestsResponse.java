package dev.squadx.controlpanel.mcp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Esqueleto de testes derivado dos cenários (RFC-0001 §4.6, ADR-0005). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScaffoldTestsResponse {

    @JsonProperty("class_name")
    private String className;

    private String file;
    private List<Method> methods;
    private Coverage coverage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Method {
        @JsonProperty("scenario_name")
        private String scenarioName;

        @JsonProperty("method_name")
        private String methodName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Coverage {
        private int total;
        private int covered;
    }
}

