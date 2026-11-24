package dev.schooldiary.organization.internal.application;

import dev.schooldiary.organization.internal.domain.SchoolType;
import java.time.*;
import java.util.*;

public record SchoolCommand(
    String name, String address, LocalDate dateOfConstruction, SchoolType type, Long version) {}
