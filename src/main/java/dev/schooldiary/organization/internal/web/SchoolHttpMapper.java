package dev.schooldiary.organization.internal.web;

import dev.schooldiary.generated.model.*;
import dev.schooldiary.organization.internal.application.*;
import dev.schooldiary.shared.application.PageResult;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SchoolHttpMapper {
  SchoolCommand toCommand(SchoolRequest request);

  SchoolCommand toCommand(SchoolUpdateRequest request);

  SchoolResponse toResponse(SchoolView view);

  SchoolPageResponse toResponse(PageResult<SchoolView> page);
}
