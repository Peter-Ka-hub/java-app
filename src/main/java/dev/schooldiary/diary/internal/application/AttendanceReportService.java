package dev.schooldiary.diary.internal.application;

import dev.schooldiary.diary.internal.domain.AttendanceStatus;
import dev.schooldiary.diary.internal.persistence.AttendanceReportRepository;
import dev.schooldiary.diary.internal.persistence.AttendanceReportRepository.EntryRow;
import dev.schooldiary.diary.internal.persistence.AttendanceReportRepository.Filter;
import dev.schooldiary.diary.internal.persistence.SubjectRepository;
import dev.schooldiary.organization.SchoolReferences;
import dev.schooldiary.shared.access.Actor;
import dev.schooldiary.shared.access.CurrentActor;
import dev.schooldiary.shared.access.UserRole;
import dev.schooldiary.shared.application.PageResult;
import dev.schooldiary.shared.error.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AttendanceReportService {
  private final CurrentActor actors;
  private final DiaryAccessPolicy access;
  private final AttendanceReportRepository reports;
  private final SchoolReferences schools;
  private final SubjectRepository subjects;

  public AttendanceReportView mine(LocalDate from, LocalDate to, int page, int size) {
    Actor student = actors.current();
    if (student.role() != UserRole.STUDENT || student.schoolId() == null) CurrentActor.deny();
    validatePeriod(from, to);
    return report(new Filter(from, to, student.schoolId(), null, null, student.id()), page, size);
  }

  public AttendanceReportView school(
      LocalDate from,
      LocalDate to,
      UUID schoolId,
      UUID classId,
      UUID subjectId,
      int page,
      int size) {
    Actor admin = actors.requireAdmin();
    validatePeriod(from, to);
    if (schoolId != null) {
      requireSchool(admin, schoolId);
      schools.school(schoolId).orElseThrow(() -> new NotFoundException("School not found"));
    }
    if (classId != null) {
      var schoolClass =
          schools.schoolClass(classId).orElseThrow(() -> new NotFoundException("Class not found"));
      requireSchool(admin, schoolClass.getSchool().getId());
    }
    if (subjectId != null) {
      var subject =
          subjects
              .findById(subjectId)
              .orElseThrow(() -> new NotFoundException("Subject not found"));
      access.readSubject(admin, subject);
    }
    UUID scopedSchool = admin.isGlobalAdmin() ? schoolId : admin.schoolId();
    return report(new Filter(from, to, scopedSchool, classId, subjectId, null), page, size);
  }

  private AttendanceReportView report(Filter filter, int page, int size) {
    var pageable = PageRequest.of(page, size);
    var counts = reports.countByStatus(filter);
    long present = counts.getOrDefault(AttendanceStatus.PRESENT, 0L);
    long absent = counts.getOrDefault(AttendanceStatus.ABSENT, 0L);
    long late = counts.getOrDefault(AttendanceStatus.LATE, 0L);
    long total = present + absent + late;
    BigDecimal percentage =
        total == 0
            ? null
            : BigDecimal.valueOf(present + late)
                .movePointRight(2)
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    List<EntryRow> entries =
        pageable.getOffset() >= total ? List.of() : reports.findEntries(filter, pageable);
    var result =
        PageResult.from(new PageImpl<>(entries, pageable, total), AttendanceReportService::toView);
    return new AttendanceReportView(
        new AttendanceTotalsView(present, absent, late, total, percentage), result);
  }

  private static AttendanceReportEntryView toView(EntryRow entry) {
    return new AttendanceReportEntryView(
        entry.lessonId(),
        entry.subjectId(),
        entry.subjectName(),
        entry.schoolId(),
        entry.classId(),
        entry.className(),
        entry.lessonDate(),
        entry.lessonNumber(),
        entry.studentId(),
        entry.firstName(),
        entry.lastName(),
        entry.status());
  }

  private static void validatePeriod(LocalDate from, LocalDate to) {
    if (from == null
        || to == null
        || to.isBefore(from)
        || ChronoUnit.DAYS.between(from, to) >= 366) {
      throw new IllegalArgumentException(
          "Attendance report requires an inclusive period of 1–366 days");
    }
  }

  private static void requireSchool(Actor actor, UUID schoolId) {
    if (!actor.isGlobalAdmin() && !schoolId.equals(actor.schoolId())) CurrentActor.deny();
  }
}
