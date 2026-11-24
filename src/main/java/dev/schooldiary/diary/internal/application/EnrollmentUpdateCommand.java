package dev.schooldiary.diary.internal.application;

import dev.schooldiary.diary.internal.domain.EnrollmentStatus;
import java.time.*;
import java.util.*;

public record EnrollmentUpdateCommand(EnrollmentStatus status, LocalDate leftOn, Long version) {}
