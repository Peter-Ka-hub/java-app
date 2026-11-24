package dev.schooldiary.organization.internal.application;

import dev.schooldiary.organization.internal.domain.School;
import dev.schooldiary.organization.internal.persistence.SchoolRepository;
import dev.schooldiary.shared.access.CurrentActor;
import dev.schooldiary.shared.application.PageResult;
import dev.schooldiary.shared.concurrency.Versions;
import dev.schooldiary.shared.error.NotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchoolService {
  private final SchoolRepository schools;
  private final SchoolMapper mapper;
  private final CurrentActor access;

  public PageResult<SchoolView> list(int page, int size) {
    var actor = access.current();
    var result =
        schools.findAll(
            (root, query, cb) ->
                actor.isGlobalAdmin()
                    ? cb.conjunction()
                    : cb.equal(root.get("id"), actor.schoolId()),
            PageRequest.of(page, size, Sort.by("id")));
    return PageResult.from(result, mapper::toView);
  }

  public SchoolView get(UUID id) {
    var school = find(id);
    access.requireSchool(id);
    return mapper.toView(school);
  }

  @Transactional
  public SchoolView create(SchoolCommand request) {
    access.requireGlobalAdmin();
    return mapper.toView(schools.saveAndFlush(mapper.toEntity(request)));
  }

  @Transactional
  public SchoolView update(UUID id, SchoolCommand request) {
    access.requireAdmin();
    access.requireSchool(id);
    var school = find(id);
    Versions.requireCurrent(request.version(), school.getVersion());
    mapper.updateEntity(request, school);
    return mapper.toView(schools.saveAndFlush(school));
  }

  @Transactional
  public void delete(UUID id) {
    access.requireGlobalAdmin();
    schools.delete(find(id));
    schools.flush();
  }

  private School find(UUID id) {
    return schools.findById(id).orElseThrow(() -> new NotFoundException("School not found: " + id));
  }
}
