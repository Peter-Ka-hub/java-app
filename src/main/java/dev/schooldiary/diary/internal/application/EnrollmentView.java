package dev.schooldiary.diary.internal.application;

import dev.schooldiary.diary.internal.domain.EnrollmentStatus;
import java.time.*;
import java.util.*;

public record EnrollmentView(
    UUID id,
    UUID subjectId,
    UUID studentId,
    LocalDate joinedOn,
    LocalDate leftOn,
    EnrollmentStatus status,
    Long version) {}
