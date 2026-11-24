package dev.schooldiary.diary.internal.persistence;

import dev.schooldiary.identity.AccountUsage;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiaryAccountUsage implements AccountUsage {
  private final SubjectRepository subjects;
  private final EnrollmentRepository enrollments;
  private final GradeRepository grades;
  private final GradeRevisionRepository revisions;
  private final AttendanceEntryRepository attendance;
  private final AttendanceCorrectionRepository attendanceCorrections;

  public boolean hasAcademicRecords(UUID id) {
    return subjects.existsByTeacherId(id)
        || enrollments.existsByStudentId(id)
        || grades.existsByStudentId(id)
        || grades.existsByGradedById(id)
        || revisions.existsByStudentId(id)
        || revisions.existsByChangedById(id)
        || attendance.existsByStudentId(id)
        || attendanceCorrections.existsByChangedById(id);
  }
}
