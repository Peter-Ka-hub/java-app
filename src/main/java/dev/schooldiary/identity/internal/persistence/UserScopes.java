package dev.schooldiary.identity.internal.persistence;

import dev.schooldiary.identity.TeacherAccountScope;
import dev.schooldiary.identity.internal.domain.User;
import dev.schooldiary.shared.access.*;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserScopes {
  private final TeacherAccountScope roster;

  public Specification<User> users(Actor actor) {
    return (root, query, cb) -> {
      if (query.getResultType() == User.class) root.fetch("school", JoinType.LEFT);
      if (actor.isGlobalAdmin()) return cb.conjunction();
      if (actor.role() == UserRole.ADMIN)
        return cb.equal(root.get("school").get("id"), actor.schoolId());
      if (actor.role() == UserRole.STUDENT) return cb.equal(root.get("id"), actor.id());
      return cb.and(
          cb.equal(root.get("school").get("id"), actor.schoolId()),
          roster.visibleTo(actor, root.get("id"), query, cb));
    };
  }
}
