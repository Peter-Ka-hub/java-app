package dev.schooldiary.organization.internal.application;

import dev.schooldiary.organization.AcademicUsage;
import dev.schooldiary.organization.internal.domain.AcademicYear;
import dev.schooldiary.organization.internal.domain.School;
import dev.schooldiary.organization.internal.domain.SchoolClass;
import dev.schooldiary.organization.internal.persistence.AcademicYearRepository;
import dev.schooldiary.organization.internal.persistence.SchoolClassRepository;
import dev.schooldiary.organization.internal.persistence.SchoolRepository;
import dev.schooldiary.shared.access.Actor;
import dev.schooldiary.shared.access.CurrentActor;
import dev.schooldiary.shared.application.PageResult;
import dev.schooldiary.shared.concurrency.Versions;
import dev.schooldiary.shared.error.ConflictException;
import dev.schooldiary.shared.error.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AcademicService {
  private final AcademicYearRepository years;
  private final SchoolClassRepository classes;
  private final SchoolRepository schools;
  private final AcademicUsage subjects;
  private final AcademicYearMapper yearMapper;
  private final SchoolClassMapper classMapper;
  private final CurrentActor access;
  private final EntityManager entityManager;

  public PageResult<AcademicYearView> listYears(int page, int size) {
    Actor actor = access.current();
    Specification<AcademicYear> scope =
        (root, query, cb) ->
            actor.isGlobalAdmin()
                ? cb.conjunction()
                : cb.equal(root.get("school").get("id"), actor.schoolId());
    var result = years.findAll(scope, PageRequest.of(page, size, Sort.by("id")));
    return PageResult.from(result, yearMapper::toView);
  }

  public AcademicYearView getYear(UUID id) {
    AcademicYear year = findYear(id);
    access.requireSchool(year.getSchool().getId());
    return yearMapper.toView(year);
  }

  @Transactional
  public AcademicYearView createYear(AcademicYearCommand request) {
    access.requireAdmin();
    access.requireSchool(request.schoolId());
    validDates(request.startDate(), request.endDate());
    AcademicYear year = new AcademicYear();
    setYear(year, request, findSchool(request.schoolId()));
    return yearMapper.toView(years.saveAndFlush(year));
  }

  @Transactional
  public AcademicYearView updateYear(UUID id, AcademicYearCommand request) {
    access.requireAdmin();
    AcademicYear year = findYear(id);
    entityManager.refresh(year, LockModeType.PESSIMISTIC_WRITE);
    access.requireSchool(year.getSchool().getId());
    access.requireSchool(request.schoolId());
    Versions.requireCurrent(request.version(), year.getVersion());
    validDates(request.startDate(), request.endDate());
    if (!Objects.equals(year.getSchool().getId(), request.schoolId())
        && (subjects.hasSubjectsForYear(id)
            || classes.exists(
                (root, query, cb) -> cb.equal(root.get("academicYear").get("id"), id)))) {
      throw new ConflictException("An academic year in use cannot be moved to another school");
    }
    if (!year.getStartDate().equals(request.startDate())
        || !year.getEndDate().equals(request.endDate())) {
      if (subjects.hasSubjectsForYear(id)) {
        throw new ConflictException(
            "Academic year dates cannot change after subjects are assigned");
      }
    }
    setYear(year, request, findSchool(request.schoolId()));
    return yearMapper.toView(years.saveAndFlush(year));
  }

  @Transactional
  public void deleteYear(UUID id) {
    access.requireAdmin();
    AcademicYear year = findYear(id);
    entityManager.refresh(year, LockModeType.PESSIMISTIC_WRITE);
    access.requireSchool(year.getSchool().getId());
    if (subjects.hasSubjectsForYear(id)
        || classes.exists((root, query, cb) -> cb.equal(root.get("academicYear").get("id"), id))) {
      throw new ConflictException(
          "Remove the academic year's classes and subjects before deleting it");
    }
    years.delete(year);
    years.flush();
  }

  public PageResult<ClassView> listClasses(int page, int size) {
    Actor actor = access.current();
    Specification<SchoolClass> scope =
        (root, query, cb) ->
            actor.isGlobalAdmin()
                ? cb.conjunction()
                : cb.equal(root.get("school").get("id"), actor.schoolId());
    var result = classes.findAll(scope, PageRequest.of(page, size, Sort.by("id")));
    return PageResult.from(result, classMapper::toView);
  }

  public ClassView getClass(UUID id) {
    SchoolClass schoolClass = findClass(id);
    access.requireSchool(schoolClass.getSchool().getId());
    return classMapper.toView(schoolClass);
  }

  @Transactional
  public ClassView createClass(ClassCommand request) {
    access.requireAdmin();
    access.requireSchool(request.schoolId());
    AcademicYear year = findYear(request.academicYearId());
    access.requireSchool(year.getSchool().getId());
    sameSchool(request.schoolId(), year);
    SchoolClass schoolClass = new SchoolClass();
    setClass(schoolClass, request, year);
    return classMapper.toView(classes.saveAndFlush(schoolClass));
  }

  @Transactional
  public ClassView updateClass(UUID id, ClassCommand request) {
    access.requireAdmin();
    SchoolClass schoolClass = findClass(id);
    access.requireSchool(schoolClass.getSchool().getId());
    access.requireSchool(request.schoolId());
    Versions.requireCurrent(request.version(), schoolClass.getVersion());
    AcademicYear year = findYear(request.academicYearId());
    access.requireSchool(year.getSchool().getId());
    sameSchool(request.schoolId(), year);
    boolean contextChanged =
        !schoolClass.getSchool().getId().equals(request.schoolId())
            || !schoolClass.getAcademicYear().getId().equals(request.academicYearId());
    if (contextChanged && subjects.hasAcademicRecordsForClass(id)) {
      throw new ConflictException(
          "A class with assigned subjects or attendance records cannot change school or year");
    }
    setClass(schoolClass, request, year);
    return classMapper.toView(classes.saveAndFlush(schoolClass));
  }

  @Transactional
  public void deleteClass(UUID id) {
    access.requireAdmin();
    SchoolClass schoolClass = findClass(id);
    access.requireSchool(schoolClass.getSchool().getId());
    if (subjects.hasAcademicRecordsForClass(id)) {
      throw new ConflictException(
          "A class with assigned subjects or attendance records cannot be deleted");
    }
    classes.delete(schoolClass);
    classes.flush();
  }

  private void validDates(LocalDate start, LocalDate end) {
    if (!start.isBefore(end)) throw new IllegalArgumentException("endDate must be after startDate");
  }

  private void sameSchool(UUID schoolId, AcademicYear year) {
    if (!year.getSchool().getId().equals(schoolId)) {
      throw new IllegalArgumentException(
          "The class and academic year must belong to the same school");
    }
  }

  private void setYear(AcademicYear year, AcademicYearCommand request, School school) {
    year.setName(request.name().strip());
    year.setSchool(school);
    year.setStartDate(request.startDate());
    year.setEndDate(request.endDate());
  }

  private void setClass(SchoolClass schoolClass, ClassCommand request, AcademicYear year) {
    schoolClass.setName(request.name().strip());
    schoolClass.setSchool(year.getSchool());
    schoolClass.setAcademicYear(year);
  }

  private AcademicYear findYear(UUID id) {
    return years
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Academic year not found: " + id));
  }

  private SchoolClass findClass(UUID id) {
    return classes.findById(id).orElseThrow(() -> new NotFoundException("Class not found: " + id));
  }

  private School findSchool(UUID id) {
    return schools.findById(id).orElseThrow(() -> new NotFoundException("School not found: " + id));
  }
}
