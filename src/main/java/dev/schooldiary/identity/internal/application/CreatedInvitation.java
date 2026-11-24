package dev.schooldiary.identity.internal.application;

public record CreatedInvitation(InvitationView invitation, String token) {
  @Override
  public String toString() {
    return "CreatedInvitation[id=" + invitation.id() + "]";
  }
}
