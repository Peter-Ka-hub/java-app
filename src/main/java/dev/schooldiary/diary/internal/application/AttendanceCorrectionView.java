package dev.schooldiary.diary.internal.application;

import dev.schooldiary.diary.internal.domain.AttendanceStatus;
import java.time.Instant;
import java.util.UUID;

public record AttendanceCorrectionView(
    AttendanceStatus previousStatus,
    AttendanceStatus newStatus,
    UUID changedById,
    Instant changedAt) {}
