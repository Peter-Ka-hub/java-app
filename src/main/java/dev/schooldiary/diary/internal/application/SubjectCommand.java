package dev.schooldiary.diary.internal.application;

import java.time.*;
import java.util.*;

public record SubjectCommand(
    String name, UUID teacherId, UUID schoolId, UUID academicYearId, UUID classId, Long version) {}
