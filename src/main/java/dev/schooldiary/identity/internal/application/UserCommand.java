package dev.schooldiary.identity.internal.application;

import dev.schooldiary.shared.access.UserRole;
import java.time.*;
import java.util.*;

public record UserCommand(
    String firstName,
    String lastName,
    String email,
    UserRole role,
    LocalDate dateOfBirth,
    UUID schoolId,
    String password,
    Boolean enabled,
    Long version) {}
