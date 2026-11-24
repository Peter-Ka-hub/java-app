package dev.schooldiary.diary.internal.application;

import dev.schooldiary.diary.internal.domain.AttendanceStatus;
import java.util.UUID;

public record AttendanceEntryCommand(UUID studentId, AttendanceStatus status) {}
