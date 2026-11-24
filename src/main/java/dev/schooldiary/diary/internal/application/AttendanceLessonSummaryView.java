package dev.schooldiary.diary.internal.application;

import dev.schooldiary.diary.internal.domain.AttendanceLessonState;
import java.time.LocalDate;
import java.util.UUID;

public record AttendanceLessonSummaryView(
    UUID id,
    UUID subjectId,
    UUID schoolId,
    UUID classId,
    LocalDate lessonDate,
    int lessonNumber,
    AttendanceLessonState state,
    long version) {}
