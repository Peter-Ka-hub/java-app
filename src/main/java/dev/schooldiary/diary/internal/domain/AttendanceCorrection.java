package dev.schooldiary.diary.internal.domain;

import dev.schooldiary.identity.internal.domain.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** An immutable audit record for a change made after attendance was finalized. */
@Entity
@Table(name = "attendance_correction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceCorrection {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "entry_id", nullable = false, updatable = false)
  private AttendanceEntry entry;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20, updatable = false)
  private AttendanceStatus previousStatus;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20, updatable = false)
  private AttendanceStatus newStatus;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "changed_by_id", nullable = false, updatable = false)
  private User changedBy;

  @Column(nullable = false, updatable = false)
  private Instant changedAt;

  public AttendanceCorrection(
      AttendanceEntry entry, AttendanceStatus newStatus, User changedBy, Instant changedAt) {
    this.entry = entry;
    this.previousStatus = entry.getStatus();
    this.newStatus = newStatus;
    this.changedBy = changedBy;
    this.changedAt = changedAt;
  }
}
