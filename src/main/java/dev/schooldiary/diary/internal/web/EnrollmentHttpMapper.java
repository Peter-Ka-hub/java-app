package dev.schooldiary.diary.internal.web;

import dev.schooldiary.diary.internal.application.*;
import dev.schooldiary.generated.model.*;
import dev.schooldiary.shared.application.PageResult;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EnrollmentHttpMapper {
  EnrollmentCommand toCommand(EnrollmentRequest request);

  EnrollmentResponse toResponse(EnrollmentView view);

  EnrollmentPageResponse toResponse(PageResult<EnrollmentView> page);

  EnrollmentUpdateCommand toCommand(EnrollmentUpdateRequest request);
}
