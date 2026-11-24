package dev.schooldiary.diary.internal.application;

import dev.schooldiary.diary.internal.domain.Enrollment;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentMapper {
  public EnrollmentView toView(Enrollment enrollment) {
    return new EnrollmentView(
        enrollment.getId(),
        enrollment.getSubject().getId(),
        enrollment.getStudent().getId(),
        enrollment.getJoinedOn(),
        enrollment.getLeftOn(),
        enrollment.getStatus(),
        enrollment.getVersion());
  }
}
