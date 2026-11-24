package dev.schooldiary.organization.internal.application;

import java.time.*;
import java.util.*;

public record AcademicYearCommand(
    String name, UUID schoolId, LocalDate startDate, LocalDate endDate, Long version) {}
