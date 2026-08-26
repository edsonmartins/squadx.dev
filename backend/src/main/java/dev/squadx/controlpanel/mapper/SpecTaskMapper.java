package dev.squadx.controlpanel.mapper;

import dev.squadx.controlpanel.dto.spectask.SpecTaskResponse;
import dev.squadx.controlpanel.model.SpecTask;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SpecTaskMapper {
    @Mapping(target = "changeId", source = "change.id")
    @Mapping(target = "requirementId", source = "requirement.id")
    @Mapping(target = "requirementRef", source = "requirement.requirementId")
    @Mapping(target = "assignedUserId", source = "assignedUser.id")
    @Mapping(target = "assignedUserName", source = "assignedUser.fullName")
    @Mapping(target = "assignedAgentId", source = "assignedAgent.id")
    @Mapping(target = "assignedAgentName", source = "assignedAgent.name")
    SpecTaskResponse toResponse(SpecTask task);
}
