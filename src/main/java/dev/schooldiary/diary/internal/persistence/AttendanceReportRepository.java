package dev.schooldiary.diary.internal.persistence;

import dev.schooldiary.diary.internal.domain.AttendanceEntry;
import dev.schooldiary.diary.internal.domain.AttendanceLessonState;
import dev.schooldiary.diary.internal.domain.AttendanceStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AttendanceReportRepository {
  private final EntityManager entityManager;

  public Map<AttendanceStatus, Long> countByStatus(Filter filter) {
    var builder = entityManager.getCriteriaBuilder();
    var query = builder.createTupleQuery();
    var entry = query.from(AttendanceEntry.class);
    var lesson = entry.join("lesson");
    var status = entry.<AttendanceStatus>get("status");
    var count = builder.count(entry);
    query
        .select(builder.tuple(status, count))
        .where(matches(builder, filter, entry, lesson))
        .groupBy(status);

    Map<AttendanceStatus, Long> totals = new EnumMap<>(AttendanceStatus.class);
    for (var row : entityManager.createQuery(query).getResultList()) {
      totals.put(row.get(status), row.get(count));
    }
    return totals;
  }

  public List<EntryRow> findEntries(Filter filter, Pageable page) {
    var builder = entityManager.getCriteriaBuilder();
    var query = builder.createQuery(EntryRow.class);
    var entry = query.from(AttendanceEntry.class);
    var lesson = entry.join("lesson");
    var subject = lesson.join("subject");
    var schoolClass = lesson.join("schoolClass", JoinType.LEFT);
    var student = entry.join("student");
    query
        .select(
            builder.construct(
                EntryRow.class,
                lesson.get("id"),
                subject.get("id"),
                subject.get("name"),
                subject.get("school").get("id"),
                schoolClass.get("id"),
                schoolClass.get("name"),
                lesson.get("lessonDate"),
                lesson.get("lessonNumber"),
                student.get("id"),
                student.get("firstName"),
                student.get("lastName"),
                entry.get("status")))
        .where(matches(builder, filter, entry, lesson))
        .orderBy(
            builder.desc(lesson.get("lessonDate")),
            builder.desc(lesson.get("lessonNumber")),
            builder.asc(lesson.get("id")),
            builder.asc(student.get("id")));
    return entityManager
        .createQuery(query)
        .setFirstResult(Math.toIntExact(page.getOffset()))
        .setMaxResults(page.getPageSize())
        .getResultList();
  }

  private static Predicate matches(
      CriteriaBuilder builder, Filter filter, Path<AttendanceEntry> entry, Path<?> lesson) {
    List<Predicate> conditions = new ArrayList<>();
    conditions.add(builder.equal(lesson.get("state"), AttendanceLessonState.FINALIZED));
    conditions.add(builder.between(lesson.get("lessonDate"), filter.from(), filter.to()));
    conditions.add(builder.isNotNull(entry.get("status")));
    if (filter.schoolId() != null) {
      conditions.add(
          builder.equal(lesson.get("subject").get("school").get("id"), filter.schoolId()));
    }
    if (filter.classId() != null) {
      conditions.add(builder.equal(lesson.get("schoolClass").get("id"), filter.classId()));
    }
    if (filter.subjectId() != null) {
      conditions.add(builder.equal(lesson.get("subject").get("id"), filter.subjectId()));
    }
    if (filter.studentId() != null) {
      conditions.add(builder.equal(entry.get("student").get("id"), filter.studentId()));
    }
    return builder.and(conditions);
  }

  public record Filter(
      LocalDate from, LocalDate to, UUID schoolId, UUID classId, UUID subjectId, UUID studentId) {}

  public record EntryRow(
      UUID lessonId,
      UUID subjectId,
      String subjectName,
      UUID schoolId,
      UUID classId,
      String className,
      LocalDate lessonDate,
      int lessonNumber,
      UUID studentId,
      String firstName,
      String lastName,
      AttendanceStatus status) {}
}
