package dev.schooldiary.organization.internal.web;

import dev.schooldiary.generated.api.AcademicYearsApi;
import dev.schooldiary.generated.model.*;
import dev.schooldiary.organization.internal.application.AcademicService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AcademicYearController implements AcademicYearsApi {
  private final AcademicService service;
  private final AcademicYearHttpMapper mapper;

  @Override
  public ResponseEntity<AcademicYearPageResponse> findAllAcademicYears(Integer page, Integer size) {
    return ResponseEntity.ok(mapper.toResponse(service.listYears(page, size)));
  }

  @Override
  public ResponseEntity<AcademicYearResponse> getAcademicYearById(UUID id) {
    return ResponseEntity.ok(mapper.toResponse(service.getYear(id)));
  }

  @Override
  public ResponseEntity<AcademicYearResponse> createAcademicYear(AcademicYearRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.toResponse(service.createYear(mapper.toCommand(request))));
  }

  @Override
  public ResponseEntity<AcademicYearResponse> updateAcademicYear(
      UUID id, AcademicYearUpdateRequest request) {
    return ResponseEntity.ok(mapper.toResponse(service.updateYear(id, mapper.toCommand(request))));
  }

  @Override
  public ResponseEntity<Void> deleteAcademicYear(UUID id) {
    service.deleteYear(id);
    return ResponseEntity.noContent().build();
  }
}
