package dev.squadx.dto.template;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateResponse {

    private String name;
    private String description;
    private String icon;
    private List<TemplateAgent> agents;

    @JsonProperty("default_tasks")
    private List<TemplateTask> defaultTasks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateAgent {
        private String name;
        private String type;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateTask {
        private String subject;

        @JsonProperty("assign_to")
        private String assignTo;

        private String priority;
    }
}
