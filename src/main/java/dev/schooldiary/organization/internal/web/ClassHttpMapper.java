package dev.schooldiary.organization.internal.web;

import dev.schooldiary.generated.model.*;
import dev.schooldiary.organization.internal.application.*;
import dev.schooldiary.shared.application.PageResult;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ClassHttpMapper {
  ClassCommand toCommand(ClassRequest request);

  ClassCommand toCommand(ClassUpdateRequest request);

  ClassResponse toResponse(ClassView view);

  ClassPageResponse toResponse(PageResult<ClassView> page);
}
