package dev.schooldiary.diary.internal.persistence;

import dev.schooldiary.diary.internal.domain.*;
import dev.schooldiary.shared.access.*;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class GradeScopes {
  public Specification<Grade> grades(Actor actor) {
    return (root, query, cb) -> {
      if (query.getResultType() == Grade.class) {
        root.fetch("student", JoinType.LEFT);
        root.fetch("gradedBy", JoinType.LEFT);
        root.fetch("subject", JoinType.LEFT);
      }
      if (actor.isGlobalAdmin()) return cb.conjunction();
      var school = cb.equal(root.get("subject").get("school").get("id"), actor.schoolId());
      if (actor.role() == UserRole.ADMIN) return school;
      return cb.and(
          school,
          actor.role() == UserRole.TEACHER
              ? cb.equal(root.get("subject").get("teacher").get("id"), actor.id())
              : cb.equal(root.get("student").get("id"), actor.id()));
    };
  }
}
