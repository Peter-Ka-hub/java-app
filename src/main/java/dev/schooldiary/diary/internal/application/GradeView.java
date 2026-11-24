package dev.schooldiary.diary.internal.application;

import dev.schooldiary.diary.internal.domain.Score;
import java.time.*;
import java.util.*;

public record GradeView(
    UUID id,
    Score score,
    LocalDate dateOfScore,
    UUID studentId,
    UUID subjectId,
    UUID gradedById,
    Instant createdAt,
    Instant updatedAt,
    Long version) {}
