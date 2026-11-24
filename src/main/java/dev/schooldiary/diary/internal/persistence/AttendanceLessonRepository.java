package dev.schooldiary.diary.internal.persistence;

import dev.schooldiary.diary.internal.domain.AttendanceLesson;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceLessonRepository extends JpaRepository<AttendanceLesson, UUID> {
  Page<AttendanceLesson> findAllBySubjectId(UUID subjectId, Pageable pageable);

  boolean existsBySubjectIdAndLessonDateAndLessonNumber(
      UUID subjectId, LocalDate lessonDate, int lessonNumber);

  boolean existsBySchoolClassId(UUID schoolClassId);
}
