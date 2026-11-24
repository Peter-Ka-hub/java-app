package dev.schooldiary.diary.internal.application;

import dev.schooldiary.diary.internal.domain.AttendanceStatus;
import java.time.LocalDate;
import java.util.UUID;

public record AttendanceReportEntryView(
    UUID lessonId,
    UUID subjectId,
    String subjectName,
    UUID schoolId,
    UUID classId,
    String className,
    LocalDate lessonDate,
    int lessonNumber,
    UUID studentId,
    String firstName,
    String lastName,
    AttendanceStatus status) {}
