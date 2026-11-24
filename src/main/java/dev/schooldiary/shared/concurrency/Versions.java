package dev.schooldiary.shared.concurrency;

import dev.schooldiary.shared.error.ConflictException;
import java.util.Objects;

public final class Versions {
  private Versions() {}

  public static void requireCurrent(Long requested, Long current) {
    if (requested == null) throw new IllegalArgumentException("version is required for updates");
    if (!Objects.equals(requested, current))
      throw new ConflictException(
          "Resource changed. Reload it and retry with its current version.");
  }
}
