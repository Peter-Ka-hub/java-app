package dev.schooldiary.identity.internal.web;

import dev.schooldiary.generated.api.InvitationsApi;
import dev.schooldiary.generated.model.InvitationAcceptRequest;
import dev.schooldiary.generated.model.InvitationCreateRequest;
import dev.schooldiary.generated.model.InvitationCreatedResponse;
import dev.schooldiary.generated.model.InvitationPageResponse;
import dev.schooldiary.generated.model.InvitationResponse;
import dev.schooldiary.generated.model.InvitationRole;
import dev.schooldiary.generated.model.InvitationStatus;
import dev.schooldiary.identity.internal.application.InvitationService;
import dev.schooldiary.identity.internal.application.InvitationView;
import dev.schooldiary.shared.access.UserRole;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InvitationController implements InvitationsApi {
  private final InvitationService invitations;

  @Override
  public ResponseEntity<InvitationPageResponse> listInvitations(Integer page, Integer size) {
    var result = invitations.list(page, size);
    return ResponseEntity.ok(
        new InvitationPageResponse(
            result.items().stream().map(InvitationController::toResponse).toList(),
            result.page(),
            result.size(),
            result.totalElements(),
            result.totalPages()));
  }

  @Override
  public ResponseEntity<InvitationCreatedResponse> createInvitation(
      InvitationCreateRequest request) {
    var created =
        invitations.create(
            request.getEmail(), UserRole.valueOf(request.getRole().name()), request.getSchoolId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(new InvitationCreatedResponse(toResponse(created.invitation()), created.token()));
  }

  @Override
  public ResponseEntity<Void> revokeInvitation(UUID id) {
    invitations.revoke(id);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> acceptInvitation(InvitationAcceptRequest request) {
    invitations.accept(
        request.getToken(),
        request.getFirstName(),
        request.getLastName(),
        request.getDateOfBirth(),
        request.getPassword());
    return ResponseEntity.noContent().build();
  }

  private static InvitationResponse toResponse(InvitationView invitation) {
    return new InvitationResponse(
        invitation.id(),
        invitation.email(),
        InvitationRole.valueOf(invitation.role().name()),
        invitation.schoolId(),
        invitation.expiresAt().atOffset(ZoneOffset.UTC),
        InvitationStatus.valueOf(invitation.status().name()));
  }
}
