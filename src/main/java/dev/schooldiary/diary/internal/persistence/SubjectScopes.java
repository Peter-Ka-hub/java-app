package dev.schooldiary.diary.internal.persistence;

import dev.schooldiary.diary.internal.domain.*;
import dev.schooldiary.shared.access.*;
import jakarta.persistence.criteria.JoinType;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class SubjectScopes {
  public Specification<Subject> subjects(Actor actor) {
    return (root, query, cb) -> {
      if (query.getResultType() == Subject.class) {
        root.fetch("school", JoinType.LEFT);
        root.fetch("teacher", JoinType.LEFT);
        root.fetch("academicYear", JoinType.LEFT);
        root.fetch("schoolClass", JoinType.LEFT);
      }
      if (actor.isGlobalAdmin()) return cb.conjunction();
      var school = cb.equal(root.get("school").get("id"), actor.schoolId());
      if (actor.role() == UserRole.ADMIN) return school;
      if (actor.role() == UserRole.TEACHER)
        return cb.and(school, cb.equal(root.get("teacher").get("id"), actor.id()));
      var sub = query.subquery(UUID.class);
      var enrollment = sub.from(Enrollment.class);
      sub.select(enrollment.get("id"))
          .where(
              cb.equal(enrollment.get("subject").get("id"), root.get("id")),
              cb.equal(enrollment.get("student").get("id"), actor.id()));
      return cb.and(school, cb.exists(sub));
    };
  }
}
