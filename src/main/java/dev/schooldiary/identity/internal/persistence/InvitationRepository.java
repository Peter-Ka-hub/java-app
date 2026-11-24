package dev.schooldiary.identity.internal.persistence;

import dev.schooldiary.identity.internal.domain.AccountInvitation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface InvitationRepository extends JpaRepository<AccountInvitation, UUID> {
  Page<AccountInvitation> findAllBySchool_Id(UUID schoolId, Pageable page);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<AccountInvitation> findLockedById(UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<AccountInvitation> findLockedByIdAndSchool_Id(UUID id, UUID schoolId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<AccountInvitation> findLockedByTokenHash(String tokenHash);
}
