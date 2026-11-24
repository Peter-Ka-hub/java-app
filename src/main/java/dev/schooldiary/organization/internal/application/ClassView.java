package dev.schooldiary.organization.internal.application;

import java.time.*;
import java.util.*;

public record ClassView(UUID id, String name, UUID schoolId, UUID academicYearId, Long version) {}
