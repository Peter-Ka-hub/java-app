package dev.schooldiary.diary.internal.web;

import dev.schooldiary.diary.internal.application.SubjectService;
import dev.schooldiary.generated.api.SubjectsApi;
import dev.schooldiary.generated.model.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SubjectController implements SubjectsApi {
  private final SubjectService service;
  private final SubjectHttpMapper mapper;

  @Override
  public ResponseEntity<SubjectPageResponse> findAllSubjects(Integer page, Integer size) {
    return ResponseEntity.ok(mapper.toResponse(service.list(page, size)));
  }

  @Override
  public ResponseEntity<SubjectResponse> getSubjectById(UUID id) {
    return ResponseEntity.ok(mapper.toResponse(service.get(id)));
  }

  @Override
  public ResponseEntity<SubjectResponse> createSubject(SubjectRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.toResponse(service.create(mapper.toCommand(request))));
  }

  @Override
  public ResponseEntity<SubjectResponse> updateSubject(UUID id, SubjectUpdateRequest request) {
    return ResponseEntity.ok(mapper.toResponse(service.update(id, mapper.toCommand(request))));
  }

  @Override
  public ResponseEntity<Void> deleteSubject(UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
