package dev.squadx.controlpanel.mcp.dto;

import dev.squadx.controlpanel.dto.change.ChangeResponse;
import dev.squadx.controlpanel.dto.requirement.RequirementResponse;
import dev.squadx.controlpanel.dto.spectask.SpecTaskResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Briefing da mudança para o agente (RFC-0001 §4.1). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetChangeResponse {

    private ChangeResponse change;
    private List<RequirementResponse> requirements;
    private List<SpecTaskResponse> tasks;
}
