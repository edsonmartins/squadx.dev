package dev.squadx.controlpanel.mapper;

import dev.squadx.controlpanel.dto.requirement.RequirementResponse;
import dev.squadx.controlpanel.model.Requirement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RequirementMapper {
    @Mapping(target = "changeId", source = "change.id")
    RequirementResponse toResponse(Requirement requirement);
}
