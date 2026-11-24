package dev.schooldiary.organization.internal.web;

import dev.schooldiary.generated.api.ClassesApi;
import dev.schooldiary.generated.model.*;
import dev.schooldiary.organization.internal.application.AcademicService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SchoolClassController implements ClassesApi {
  private final AcademicService service;
  private final ClassHttpMapper mapper;

  @Override
  public ResponseEntity<ClassPageResponse> findAllClasses(Integer page, Integer size) {
    return ResponseEntity.ok(mapper.toResponse(service.listClasses(page, size)));
  }

  @Override
  public ResponseEntity<ClassResponse> getClassById(UUID id) {
    return ResponseEntity.ok(mapper.toResponse(service.getClass(id)));
  }

  @Override
  public ResponseEntity<ClassResponse> createClass(ClassRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.toResponse(service.createClass(mapper.toCommand(request))));
  }

  @Override
  public ResponseEntity<ClassResponse> updateClass(UUID id, ClassUpdateRequest request) {
    return ResponseEntity.ok(mapper.toResponse(service.updateClass(id, mapper.toCommand(request))));
  }

  @Override
  public ResponseEntity<Void> deleteClass(UUID id) {
    service.deleteClass(id);
    return ResponseEntity.noContent().build();
  }
}
