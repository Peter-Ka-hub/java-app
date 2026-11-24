package dev.schooldiary.diary.internal.application;

import java.time.*;
import java.util.*;

public record SubjectView(
    UUID id,
    String name,
    UUID teacherId,
    Set<UUID> studentIds,
    UUID schoolId,
    UUID academicYearId,
    UUID classId,
    Long version) {}
