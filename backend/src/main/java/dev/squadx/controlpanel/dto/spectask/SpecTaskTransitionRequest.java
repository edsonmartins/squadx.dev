package dev.squadx.controlpanel.dto.spectask;

import dev.squadx.controlpanel.model.enums.SpecTaskStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pedido de transição de estado de uma tarefa. Alvos {@code CONCLUIDA}/{@code AJUSTES} são
 * rejeitados aqui — só o Pass 5 os atribui (work-model R5).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpecTaskTransitionRequest {

    @NotNull(message = "Target status is required")
    private SpecTaskStatus status;

    /** Obrigatório quando o alvo é BLOQUEADA (motivo do bloqueio). */
    private String note;
}

