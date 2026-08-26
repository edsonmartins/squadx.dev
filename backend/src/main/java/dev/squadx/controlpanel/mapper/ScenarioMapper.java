package dev.squadx.controlpanel.mapper;

import dev.squadx.controlpanel.dto.requirement.ScenarioResponse;
import dev.squadx.controlpanel.model.Scenario;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ScenarioMapper {
    ScenarioResponse toResponse(Scenario scenario);
}
