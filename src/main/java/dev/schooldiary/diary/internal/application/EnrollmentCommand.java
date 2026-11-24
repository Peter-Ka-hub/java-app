package dev.schooldiary.diary.internal.application;

import java.time.*;
import java.util.*;

public record EnrollmentCommand(UUID subjectId, UUID studentId, LocalDate joinedOn) {}
