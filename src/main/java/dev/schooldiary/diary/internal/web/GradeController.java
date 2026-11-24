package dev.schooldiary.diary.internal.web;

import dev.schooldiary.diary.internal.application.GradeService;
import dev.schooldiary.generated.api.GradesApi;
import dev.schooldiary.generated.model.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GradeController implements GradesApi {
  private final GradeService service;
  private final GradeHttpMapper mapper;

  @Override
  public ResponseEntity<GradePageResponse> findAllGrades(Integer page, Integer size) {
    return ResponseEntity.ok(mapper.toResponse(service.list(page, size)));
  }

  @Override
  public ResponseEntity<GradeResponse> getGradeById(UUID id) {
    return ResponseEntity.ok(mapper.toResponse(service.get(id)));
  }

  @Override
  public ResponseEntity<GradeResponse> createGrade(GradeRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.toResponse(service.create(mapper.toCommand(request))));
  }

  @Override
  public ResponseEntity<GradeResponse> updateGrade(UUID id, GradeUpdateRequest request) {
    return ResponseEntity.ok(mapper.toResponse(service.update(id, mapper.toCommand(request))));
  }

  @Override
  public ResponseEntity<Void> deleteGrade(UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<java.util.List<GradeRevisionResponse>> getGradeHistory(UUID id) {
    return ResponseEntity.ok(mapper.toHistory(service.history(id)));
  }
}
