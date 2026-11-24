package dev.schooldiary.diary.internal.persistence;

import dev.schooldiary.diary.internal.domain.*;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EnrollmentRepository
    extends JpaRepository<Enrollment, UUID>, JpaSpecificationExecutor<Enrollment> {
  Optional<Enrollment> findBySubjectIdAndStudentId(UUID subjectId, UUID studentId);

  boolean existsBySubjectIdAndStudentIdAndStatus(
      UUID subjectId, UUID studentId, EnrollmentStatus status);

  boolean existsByStudentId(UUID studentId);

  boolean existsBySubjectId(UUID subjectId);

  List<Enrollment> findBySubjectIdAndStatus(UUID subjectId, EnrollmentStatus status);

  List<Enrollment> findBySubjectIdAndStatusAndJoinedOnLessThanEqual(
      UUID subjectId, EnrollmentStatus status, LocalDate lessonDate);

  List<Enrollment> findByStudentIdAndStatus(UUID studentId, EnrollmentStatus status);

  List<Enrollment> findBySubjectIdInAndStatus(Collection<UUID> subjectIds, EnrollmentStatus status);
}
