package dev.schooldiary.organization.internal.application;

import dev.schooldiary.organization.internal.domain.SchoolClass;
import org.springframework.stereotype.Component;

@Component
public class SchoolClassMapper {
  public ClassView toView(SchoolClass schoolClass) {
    return new ClassView(
        schoolClass.getId(),
        schoolClass.getName(),
        schoolClass.getSchool().getId(),
        schoolClass.getAcademicYear().getId(),
        schoolClass.getVersion());
  }
}
