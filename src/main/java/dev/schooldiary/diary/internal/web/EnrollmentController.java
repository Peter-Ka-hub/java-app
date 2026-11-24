package dev.schooldiary.diary.internal.web;

import dev.schooldiary.diary.internal.application.EnrollmentService;
import dev.schooldiary.generated.api.EnrollmentsApi;
import dev.schooldiary.generated.model.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EnrollmentController implements EnrollmentsApi {
  private final EnrollmentService service;
  private final EnrollmentHttpMapper mapper;

  @Override
  public ResponseEntity<EnrollmentPageResponse> findAllEnrollments(Integer page, Integer size) {
    return ResponseEntity.ok(mapper.toResponse(service.list(page, size)));
  }

  @Override
  public ResponseEntity<EnrollmentResponse> getEnrollmentById(UUID id) {
    return ResponseEntity.ok(mapper.toResponse(service.get(id)));
  }

  @Override
  public ResponseEntity<EnrollmentResponse> createEnrollment(EnrollmentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.toResponse(service.create(mapper.toCommand(request))));
  }

  @Override
  public ResponseEntity<EnrollmentResponse> createEnrollmentByEmail(
      UUID id, EnrollmentByEmailRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            mapper.toResponse(
                service.createByEmail(id, request.getEmail(), request.getJoinedOn())));
  }

  @Override
  public ResponseEntity<EnrollmentResponse> updateEnrollment(
      UUID id, EnrollmentUpdateRequest request) {
    return ResponseEntity.ok(mapper.toResponse(service.update(id, mapper.toCommand(request))));
  }
}
