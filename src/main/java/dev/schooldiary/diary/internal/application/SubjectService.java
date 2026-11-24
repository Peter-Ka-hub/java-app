package dev.schooldiary.diary.internal.application;

import dev.schooldiary.diary.internal.domain.EnrollmentStatus;
import dev.schooldiary.diary.internal.domain.Subject;
import dev.schooldiary.diary.internal.persistence.EnrollmentRepository;
import dev.schooldiary.diary.internal.persistence.GradeRepository;
import dev.schooldiary.diary.internal.persistence.GradeRevisionRepository;
import dev.schooldiary.diary.internal.persistence.SubjectRepository;
import dev.schooldiary.diary.internal.persistence.SubjectScopes;
import dev.schooldiary.identity.AccountLookup;
import dev.schooldiary.organization.SchoolReferences;
import dev.schooldiary.shared.access.CurrentActor;
import dev.schooldiary.shared.access.UserRole;
import dev.schooldiary.shared.application.PageResult;
import dev.schooldiary.shared.concurrency.Versions;
import dev.schooldiary.shared.error.ConflictException;
import dev.schooldiary.shared.error.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubjectService {
  private final SubjectRepository subjects;
  private final AccountLookup accounts;
  private final SchoolReferences schoolReferences;
  private final EnrollmentRepository enrollments;
  private final GradeRepository grades;
  private final GradeRevisionRepository revisions;
  private final SubjectMapper mapper;
  private final DiaryAccessPolicy access;
  private final CurrentActor actors;
  private final SubjectScopes scopes;
  private final EntityManager entityManager;

  public PageResult<SubjectView> list(int page, int size) {
    var actor = actors.current();
    var result =
        subjects.findAll(scopes.subjects(actor), PageRequest.of(page, size, Sort.by("id")));
    var ids = result.getContent().stream().map(Subject::getId).toList();
    Map<UUID, Set<UUID>> roster = new HashMap<>();
    if (!ids.isEmpty())
      for (var enrollment : enrollments.findBySubjectIdInAndStatus(ids, EnrollmentStatus.ACTIVE)) {
        if (actor.role() != UserRole.STUDENT || actor.id().equals(enrollment.getStudent().getId()))
          roster
              .computeIfAbsent(enrollment.getSubject().getId(), key -> new LinkedHashSet<>())
              .add(enrollment.getStudent().getId());
      }
    var items =
        result.getContent().stream()
            .map(
                subject -> {
                  return mapper.toView(subject, roster.getOrDefault(subject.getId(), Set.of()));
                })
            .toList();
    return new PageResult<>(items, page, size, result.getTotalElements(), result.getTotalPages());
  }

  public SubjectView get(UUID id) {
    var subject = find(id);
    access.readSubject(subject);
    return response(subject);
  }

  @Transactional
  public SubjectView create(SubjectCommand request) {
    actors.requireAdmin();
    var subject = new Subject();
    apply(request, subject);
    return response(subjects.saveAndFlush(subject));
  }

  @Transactional
  public SubjectView update(UUID id, SubjectCommand request) {
    actors.requireAdmin();
    var subject = locked(id);
    actors.requireSchool(subject.getSchool().getId());
    Versions.requireCurrent(request.version(), subject.getVersion());
    boolean contextChanged =
        !subject.getSchool().getId().equals(request.schoolId())
            || !subject.getAcademicYear().getId().equals(request.academicYearId());
    if (contextChanged
        && (enrollments.existsBySubjectId(id)
            || grades.existsBySubjectId(id)
            || revisions.existsBySubjectId(id)))
      throw new ConflictException(
          "A subject with academic records cannot change school or academic year");
    apply(request, subject);
    return response(subjects.saveAndFlush(subject));
  }

  @Transactional
  public void delete(UUID id) {
    actors.requireAdmin();
    var subject = locked(id);
    actors.requireSchool(subject.getSchool().getId());
    if (enrollments.existsBySubjectId(id)
        || grades.existsBySubjectId(id)
        || revisions.existsBySubjectId(id))
      throw new ConflictException("Subject has academic records and cannot be deleted");
    subjects.delete(subject);
    subjects.flush();
  }

  private void apply(SubjectCommand request, Subject subject) {
    actors.requireSchool(request.schoolId());
    var school =
        schoolReferences
            .school(request.schoolId())
            .orElseThrow(() -> new NotFoundException("School not found"));
    var teacher = accounts.locked(request.teacherId()).orElse(null);
    if (teacher == null) throw new NotFoundException("Teacher not found");
    if (teacher.getRole() != UserRole.TEACHER || !teacher.isEnabled())
      throw new IllegalArgumentException("Teacher must be an enabled TEACHER");
    if (!school.getId().equals(teacher.schoolId()))
      throw new IllegalArgumentException("Teacher must belong to the subject's school");
    var year =
        schoolReferences
            .lockedYear(request.academicYearId())
            .orElseThrow(() -> new NotFoundException("Academic year not found"));
    if (!school.getId().equals(year.getSchool().getId()))
      throw new IllegalArgumentException("Academic year must belong to the subject's school");
    var schoolClass =
        request.classId() == null
            ? null
            : schoolReferences
                .schoolClass(request.classId())
                .orElseThrow(() -> new NotFoundException("Class not found"));
    if (schoolClass != null
        && (!school.getId().equals(schoolClass.getSchool().getId())
            || !year.getId().equals(schoolClass.getAcademicYear().getId())))
      throw new IllegalArgumentException("Class must belong to the same school and academic year");
    subject.setName(request.name().strip());
    subject.setSchool(school);
    subject.setTeacher(teacher);
    subject.setAcademicYear(year);
    subject.setSchoolClass(schoolClass);
  }

  private SubjectView response(Subject subject) {
    var actor = actors.current();
    return mapper.toView(
        subject,
        enrollments.findBySubjectIdAndStatus(subject.getId(), EnrollmentStatus.ACTIVE).stream()
            .map(e -> e.getStudent().getId())
            .filter(id -> actor.role() != UserRole.STUDENT || actor.id().equals(id))
            .collect(Collectors.toCollection(LinkedHashSet::new)));
  }

  private Subject find(UUID id) {
    return subjects
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Subject not found: " + id));
  }

  private Subject locked(UUID id) {
    var subject = entityManager.find(Subject.class, id, LockModeType.PESSIMISTIC_WRITE);
    if (subject == null) throw new NotFoundException("Subject not found: " + id);
    entityManager.refresh(subject, LockModeType.PESSIMISTIC_WRITE);
    return subject;
  }
}
