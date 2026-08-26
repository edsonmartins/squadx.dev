package dev.squadx.controlpanel.mapper;

import dev.squadx.controlpanel.dto.change.ChangeResponse;
import dev.squadx.controlpanel.model.Change;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ChangeMapper {
    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "projectName", source = "project.name")
    @Mapping(target = "createdById", source = "createdBy.id")
    @Mapping(target = "createdByName", source = "createdBy.fullName")
    ChangeResponse toResponse(Change change);
}
