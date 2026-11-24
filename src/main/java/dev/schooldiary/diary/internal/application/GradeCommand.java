package dev.schooldiary.diary.internal.application;

import dev.schooldiary.diary.internal.domain.Score;
import java.time.*;
import java.util.*;

public record GradeCommand(
    Score score, LocalDate dateOfScore, UUID studentId, UUID subjectId, Long version) {}
