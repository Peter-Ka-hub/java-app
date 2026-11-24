package dev.schooldiary.organization.internal.web;

import dev.schooldiary.generated.api.SchoolsApi;
import dev.schooldiary.generated.model.*;
import dev.schooldiary.organization.internal.application.SchoolService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SchoolController implements SchoolsApi {
  private final SchoolService service;
  private final SchoolHttpMapper mapper;

  @Override
  public ResponseEntity<SchoolPageResponse> findAllSchools(Integer page, Integer size) {
    return ResponseEntity.ok(mapper.toResponse(service.list(page, size)));
  }

  @Override
  public ResponseEntity<SchoolResponse> getSchoolById(UUID id) {
    return ResponseEntity.ok(mapper.toResponse(service.get(id)));
  }

  @Override
  public ResponseEntity<SchoolResponse> createSchool(SchoolRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.toResponse(service.create(mapper.toCommand(request))));
  }

  @Override
  public ResponseEntity<SchoolResponse> updateSchool(UUID id, SchoolUpdateRequest request) {
    return ResponseEntity.ok(mapper.toResponse(service.update(id, mapper.toCommand(request))));
  }

  @Override
  public ResponseEntity<Void> deleteSchool(UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
