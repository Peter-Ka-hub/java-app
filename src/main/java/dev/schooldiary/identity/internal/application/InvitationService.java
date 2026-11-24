package dev.schooldiary.identity.internal.application;

import dev.schooldiary.identity.internal.domain.AccountInvitation;
import dev.schooldiary.identity.internal.domain.InvitationStatus;
import dev.schooldiary.identity.internal.persistence.InvitationRepository;
import dev.schooldiary.identity.internal.persistence.UserRepository;
import dev.schooldiary.organization.SchoolReferences;
import dev.schooldiary.shared.access.CurrentActor;
import dev.schooldiary.shared.access.UserRole;
import dev.schooldiary.shared.application.PageResult;
import dev.schooldiary.shared.error.ConflictException;
import dev.schooldiary.shared.error.NotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvitationService {
  private static final Duration VALIDITY = Duration.ofHours(24);
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_-]{43}");

  private final InvitationRepository invitations;
  private final UserRepository users;
  private final UserService accounts;
  private final UserMapper userMapper;
  private final SchoolReferences schools;
  private final CurrentActor access;

  public PageResult<InvitationView> list(int page, int size) {
    var actor = access.requireAdmin();
    var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "expiresAt", "id"));
    var result =
        actor.isGlobalAdmin()
            ? invitations.findAll(pageable)
            : invitations.findAllBySchool_Id(actor.schoolId(), pageable);
    Instant now = Instant.now();
    return PageResult.from(result, invitation -> toView(invitation, now));
  }

  @Transactional
  public CreatedInvitation create(String email, UserRole role, UUID requestedSchoolId) {
    var actor = access.requireAdmin();
    UUID schoolId = requestedSchoolId == null ? actor.schoolId() : requestedSchoolId;
    if (schoolId == null) throw new IllegalArgumentException("schoolId is required");
    if (!actor.isGlobalAdmin() && !schoolId.equals(actor.schoolId())) CurrentActor.deny();
    if (role != UserRole.STUDENT && role != UserRole.TEACHER) {
      throw new IllegalArgumentException(
          "Invitations are only available for students and teachers");
    }

    var school =
        schools.school(schoolId).orElseThrow(() -> new NotFoundException("School not found"));
    String normalizedEmail = userMapper.normalizeEmail(email);
    requireAvailableEmail(normalizedEmail);

    byte[] secret = new byte[32];
    RANDOM.nextBytes(secret);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    Instant now = Instant.now();
    var invitation =
        invitations.save(
            new AccountInvitation(hash(token), normalizedEmail, role, school, now.plus(VALIDITY)));
    return new CreatedInvitation(toView(invitation, now), token);
  }

  @Transactional
  public void revoke(UUID id) {
    var actor = access.requireAdmin();
    var invitation =
        (actor.isGlobalAdmin()
                ? invitations.findLockedById(id)
                : invitations.findLockedByIdAndSchool_Id(id, actor.schoolId()))
            .orElseThrow(() -> new NotFoundException("Invitation not found"));
    Instant now = Instant.now();
    if (invitation.statusAt(now) != InvitationStatus.PENDING) {
      throw new ConflictException("Only pending invitations can be revoked");
    }
    invitation.markRevoked(now);
  }

  @Transactional
  public void accept(
      String token, String firstName, String lastName, LocalDate dateOfBirth, String password) {
    if (token == null || !TOKEN_PATTERN.matcher(token).matches()) throw unavailableInvitation();
    var invitation =
        invitations
            .findLockedByTokenHash(hash(token))
            .orElseThrow(InvitationService::unavailableInvitation);
    Instant now = Instant.now();
    if (invitation.statusAt(now) != InvitationStatus.PENDING) throw unavailableInvitation();
    requireAvailableEmail(invitation.getEmail());

    var request =
        new UserCommand(
            firstName,
            lastName,
            invitation.getEmail(),
            invitation.getRole(),
            dateOfBirth,
            invitation.getSchool().getId(),
            password,
            true,
            null);
    accounts.createAccount(request, invitation.getSchool());
    invitation.markAccepted(now);
  }

  private void requireAvailableEmail(String email) {
    if (users.existsByEmailIgnoreCase(email)) {
      throw new ConflictException("An account already exists for this email");
    }
  }

  private static NotFoundException unavailableInvitation() {
    return new NotFoundException(
        "Invitation is unavailable. Ask your school administrator for a new link.");
  }

  private static String hash(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static InvitationView toView(AccountInvitation invitation, Instant now) {
    return new InvitationView(
        invitation.getId(),
        invitation.getEmail(),
        invitation.getRole(),
        invitation.getSchool().getId(),
        invitation.getExpiresAt(),
        invitation.statusAt(now));
  }
}
