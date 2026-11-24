package dev.schooldiary.diary.internal.persistence;

import dev.schooldiary.organization.AcademicUsage;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiaryAcademicUsage implements AcademicUsage {
  private final SubjectRepository subjects;
  private final AttendanceLessonRepository lessons;

  public boolean hasSubjectsForYear(UUID id) {
    return subjects.existsByAcademicYearId(id);
  }

  public boolean hasAcademicRecordsForClass(UUID id) {
    return subjects.existsBySchoolClassId(id) || lessons.existsBySchoolClassId(id);
  }
}
