package dev.schooldiary.diary.internal.application;

import dev.schooldiary.shared.application.PageResult;

public record AttendanceReportView(
    AttendanceTotalsView summary, PageResult<AttendanceReportEntryView> entries) {}
