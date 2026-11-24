package dev.schooldiary.identity;

import java.util.UUID;

/** Inbound port: academic record owners decide whether an account is referenced. */
public interface AccountUsage {
  boolean hasAcademicRecords(UUID accountId);
}
