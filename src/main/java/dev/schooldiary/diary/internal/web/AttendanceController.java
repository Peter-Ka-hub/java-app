package dev.schooldiary.diary.internal.web;

import dev.schooldiary.diary.internal.application.AttendanceReportService;
import dev.schooldiary.diary.internal.application.AttendanceService;
import dev.schooldiary.diary.internal.domain.AttendanceLessonState;
import dev.schooldiary.generated.api.AttendanceApi;
import dev.schooldiary.generated.model.AttendanceLessonCreateRequest;
import dev.schooldiary.generated.model.AttendanceLessonPageResponse;
import dev.schooldiary.generated.model.AttendanceLessonResponse;
import dev.schooldiary.generated.model.AttendanceLessonSaveRequest;
import dev.schooldiary.generated.model.AttendanceReportResponse;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AttendanceController implements AttendanceApi {
  private final AttendanceService lessons;
  private final AttendanceReportService reports;
  private final AttendanceHttpMapper mapper;

  @Override
  public ResponseEntity<AttendanceLessonPageResponse> listAttendanceLessons(
      UUID id, Integer page, Integer size) {
    return ResponseEntity.ok(mapper.toLessonPage(lessons.list(id, page, size)));
  }

  @Override
  public ResponseEntity<AttendanceLessonResponse> createAttendanceLesson(
      UUID id, AttendanceLessonCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.toResponse(lessons.create(id, request.getLessonNumber())));
  }

  @Override
  public ResponseEntity<AttendanceLessonResponse> getAttendanceLesson(UUID id) {
    return ResponseEntity.ok(mapper.toResponse(lessons.get(id)));
  }

  @Override
  public ResponseEntity<AttendanceLessonResponse> saveAttendanceLesson(
      UUID id, AttendanceLessonSaveRequest request) {
    var entries = request.getEntries().stream().map(mapper::toCommand).toList();
    var state = AttendanceLessonState.valueOf(request.getState().name());
    return ResponseEntity.ok(
        mapper.toResponse(lessons.save(id, request.getVersion(), state, entries)));
  }

  @Override
  public ResponseEntity<AttendanceReportResponse> getMyAttendance(
      LocalDate from, LocalDate to, Integer page, Integer size) {
    return ResponseEntity.ok(mapper.toResponse(reports.mine(from, to, page, size)));
  }

  @Override
  public ResponseEntity<AttendanceReportResponse> getSchoolAttendanceReport(
      LocalDate from,
      LocalDate to,
      UUID schoolId,
      UUID classId,
      UUID subjectId,
      Integer page,
      Integer size) {
    return ResponseEntity.ok(
        mapper.toResponse(reports.school(from, to, schoolId, classId, subjectId, page, size)));
  }
}
