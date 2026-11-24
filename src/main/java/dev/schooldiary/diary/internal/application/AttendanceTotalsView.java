package dev.schooldiary.diary.internal.application;

import java.math.BigDecimal;

public record AttendanceTotalsView(
    long present, long absent, long late, long total, BigDecimal attendancePercentage) {}
