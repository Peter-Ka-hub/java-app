package dev.schooldiary.organization.internal.application;

import java.time.*;
import java.util.*;

public record AcademicYearView(
    UUID id, String name, UUID schoolId, LocalDate startDate, LocalDate endDate, Long version) {}
