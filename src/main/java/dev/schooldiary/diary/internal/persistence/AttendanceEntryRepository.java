package dev.schooldiary.diary.internal.persistence;

import dev.schooldiary.diary.internal.domain.AttendanceEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AttendanceEntryRepository extends JpaRepository<AttendanceEntry, UUID> {
  @Query(
      """
      select e from AttendanceEntry e join fetch e.student student
      where e.lesson.id = :lessonId
      order by student.lastName, student.firstName, student.id
      """)
  List<AttendanceEntry> findRoster(UUID lessonId);

  boolean existsByStudentId(UUID studentId);
}
