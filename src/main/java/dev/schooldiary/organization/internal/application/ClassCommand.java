package dev.schooldiary.organization.internal.application;

import java.time.*;
import java.util.*;

public record ClassCommand(String name, UUID schoolId, UUID academicYearId, Long version) {}
