package dev.schooldiary.diary.internal.persistence;

import dev.schooldiary.diary.internal.domain.Enrollment;
import dev.schooldiary.identity.TeacherAccountScope;
import dev.schooldiary.shared.access.Actor;
import jakarta.persistence.criteria.*;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DiaryTeacherAccountScope implements TeacherAccountScope {
  public Predicate visibleTo(
      Actor teacher, Path<UUID> accountId, CriteriaQuery<?> query, CriteriaBuilder cb) {
    var sub = query.subquery(UUID.class);
    var enrollment = sub.from(Enrollment.class);
    sub.select(enrollment.get("id"))
        .where(
            cb.equal(enrollment.get("student").get("id"), accountId),
            cb.equal(enrollment.get("subject").get("teacher").get("id"), teacher.id()));
    return cb.exists(sub);
  }
}
