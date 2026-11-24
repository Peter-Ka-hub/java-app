package dev.schooldiary.diary.internal.application;

import dev.schooldiary.diary.internal.domain.GradeRevisionAction;
import dev.schooldiary.diary.internal.domain.Score;
import java.time.*;
import java.util.*;

public record GradeRevisionView(
    UUID id,
    UUID gradeId,
    Score score,
    LocalDate dateOfScore,
    UUID studentId,
    UUID subjectId,
    UUID changedById,
    Instant changedAt,
    GradeRevisionAction action) {}
