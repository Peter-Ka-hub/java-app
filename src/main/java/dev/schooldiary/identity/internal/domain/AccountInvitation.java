package dev.schooldiary.identity.internal.domain;

import dev.schooldiary.organization.internal.domain.School;
import dev.schooldiary.shared.access.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "account_invitation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountInvitation {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, length = 64, updatable = false)
  private String tokenHash;

  @Column(nullable = false, length = 50, updatable = false)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50, updatable = false)
  private UserRole role;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "school_id", nullable = false, updatable = false)
  private School school;

  @Column(nullable = false, updatable = false)
  private Instant expiresAt;

  private Instant acceptedAt;
  private Instant revokedAt;

  public AccountInvitation(
      String tokenHash, String email, UserRole role, School school, Instant expiresAt) {
    this.tokenHash = tokenHash;
    this.email = email;
    this.role = role;
    this.school = school;
    this.expiresAt = expiresAt;
  }

  public InvitationStatus statusAt(Instant now) {
    if (acceptedAt != null) return InvitationStatus.ACCEPTED;
    if (revokedAt != null) return InvitationStatus.REVOKED;
    return expiresAt.isAfter(now) ? InvitationStatus.PENDING : InvitationStatus.EXPIRED;
  }

  public void markAccepted(Instant now) {
    acceptedAt = now;
  }

  public void markRevoked(Instant now) {
    revokedAt = now;
  }
}
