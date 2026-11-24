package dev.schooldiary.diary.internal.persistence;

import dev.schooldiary.diary.internal.domain.AttendanceCorrection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrection, UUID> {
  List<AttendanceCorrection> findByEntryLessonIdOrderByChangedAtAscIdAsc(UUID lessonId);

  boolean existsByChangedById(UUID changedById);
}
