package dev.schooldiary.identity.internal.application;

import dev.schooldiary.identity.internal.domain.InvitationStatus;
import dev.schooldiary.shared.access.UserRole;
import java.time.Instant;
import java.util.UUID;

public record InvitationView(
    UUID id,
    String email,
    UserRole role,
    UUID schoolId,
    Instant expiresAt,
    InvitationStatus status) {}
