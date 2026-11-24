package dev.schooldiary.organization.internal.application;

import dev.schooldiary.organization.internal.domain.AcademicYear;
import org.springframework.stereotype.Component;

@Component
public class AcademicYearMapper {
  public AcademicYearView toView(AcademicYear year) {
    return new AcademicYearView(
        year.getId(),
        year.getName(),
        year.getSchool().getId(),
        year.getStartDate(),
        year.getEndDate(),
        year.getVersion());
  }
}
