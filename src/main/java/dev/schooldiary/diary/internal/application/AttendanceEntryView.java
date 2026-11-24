package dev.schooldiary.diary.internal.application;

import dev.schooldiary.diary.internal.domain.AttendanceStatus;
import java.util.List;
import java.util.UUID;

public record AttendanceEntryView(
    UUID studentId,
    String firstName,
    String lastName,
    AttendanceStatus status,
    List<AttendanceCorrectionView> corrections) {}
