package dev.squadx.controlpanel.mcp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionRequest {

    @NotNull(message = "change_id is required")
    @JsonProperty("change_id")
    private Long changeId;

    /** Identificador do agente que abrirá a sessão (opcional). */
    private String assignee;
}
