package dev.schooldiary.organization.internal.web;

import dev.schooldiary.generated.model.*;
import dev.schooldiary.organization.internal.application.*;
import dev.schooldiary.shared.application.PageResult;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AcademicYearHttpMapper {
  AcademicYearCommand toCommand(AcademicYearRequest request);

  AcademicYearCommand toCommand(AcademicYearUpdateRequest request);

  AcademicYearResponse toResponse(AcademicYearView view);

  AcademicYearPageResponse toResponse(PageResult<AcademicYearView> page);
}
