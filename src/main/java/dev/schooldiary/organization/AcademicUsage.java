package dev.schooldiary.organization;

import java.util.UUID;

/** Inbound port implemented by owners of subject assignments. */
public interface AcademicUsage {
  boolean hasSubjectsForYear(UUID academicYearId);

  boolean hasAcademicRecordsForClass(UUID classId);
}
