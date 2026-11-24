package dev.schooldiary.identity.internal.application;

import dev.schooldiary.shared.access.UserRole;
import java.time.*;
import java.util.*;

public record UserView(
    UUID id,
    String firstName,
    String lastName,
    String email,
    UserRole role,
    LocalDate dateOfBirth,
    UUID schoolId,
    boolean enabled,
    Long version) {}
