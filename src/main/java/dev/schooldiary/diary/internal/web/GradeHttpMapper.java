package dev.schooldiary.diary.internal.web;

import dev.schooldiary.diary.internal.application.*;
import dev.schooldiary.generated.model.*;
import dev.schooldiary.shared.application.PageResult;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface GradeHttpMapper {
  GradeCommand toCommand(GradeRequest request);

  GradeCommand toCommand(GradeUpdateRequest request);

  GradeResponse toResponse(GradeView view);

  GradePageResponse toResponse(PageResult<GradeView> page);

  GradeRevisionResponse toResponse(GradeRevisionView view);

  java.util.List<GradeRevisionResponse> toHistory(java.util.List<GradeRevisionView> history);

  default java.time.OffsetDateTime toDateTime(java.time.Instant instant) {
    return instant == null ? null : instant.atOffset(java.time.ZoneOffset.UTC);
  }
}
