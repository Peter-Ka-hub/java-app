package dev.schooldiary.diary.internal.application;

import java.util.List;

public record AttendanceLessonView(
    AttendanceLessonSummaryView lesson, List<AttendanceEntryView> entries) {}
