package dev.schooldiary.diary.internal.web;

import dev.schooldiary.diary.internal.application.AttendanceEntryCommand;
import dev.schooldiary.diary.internal.application.AttendanceLessonSummaryView;
import dev.schooldiary.diary.internal.application.AttendanceLessonView;
import dev.schooldiary.diary.internal.application.AttendanceReportView;
import dev.schooldiary.generated.model.AttendanceEntryUpdate;
import dev.schooldiary.generated.model.AttendanceLessonPageResponse;
import dev.schooldiary.generated.model.AttendanceLessonResponse;
import dev.schooldiary.generated.model.AttendanceReportResponse;
import dev.schooldiary.shared.application.PageResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AttendanceHttpMapper {
  AttendanceLessonResponse toResponse(AttendanceLessonView view);

  AttendanceLessonPageResponse toLessonPage(PageResult<AttendanceLessonSummaryView> page);

  AttendanceReportResponse toResponse(AttendanceReportView view);

  AttendanceEntryCommand toCommand(AttendanceEntryUpdate row);

  default OffsetDateTime toOffsetDateTime(Instant time) {
    return time == null ? null : time.atOffset(ZoneOffset.UTC);
  }
}
