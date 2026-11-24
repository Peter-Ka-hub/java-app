package com.example.demo.modules.school.controller;

import com.example.demo.api.generated.api.SchoolsApi;
import com.example.demo.api.generated.model.SchoolPageResponse;
import com.example.demo.api.generated.model.SchoolRequest;
import com.example.demo.api.generated.model.SchoolResponse;
import com.example.demo.modules.school.domain.School;
import com.example.demo.modules.school.service.SchoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class SchoolController implements SchoolsApi {

    private final SchoolService schoolService;
    private final SchoolMapper schoolMapper;

    @Override
    public ResponseEntity<SchoolPageResponse> findAllSchools(Integer page, Integer size) {
        Page<School> result = schoolService.findAll(PageRequest.of(page, size));
        return ResponseEntity.ok(new SchoolPageResponse()
                .items(schoolMapper.toResponseList(result.getContent()))
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages()));
    }

    @Override
    public ResponseEntity<SchoolResponse> getSchoolById(UUID id) {
        return ResponseEntity.ok(schoolMapper.toResponse(schoolService.findById(id)));
    }

    @Override
    public ResponseEntity<SchoolResponse> createSchool(SchoolRequest schoolRequest) {
        School saved = schoolService.create(schoolRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(schoolMapper.toResponse(saved));
    }

    @Override
    public ResponseEntity<SchoolResponse> updateSchool(UUID id, SchoolRequest schoolRequest) {
        School updated = schoolService.update(id, schoolRequest);
        return ResponseEntity.ok(schoolMapper.toResponse(updated));
    }

    @Override
    public ResponseEntity<Void> deleteSchool(UUID id) {
        schoolService.delete(id);
        return ResponseEntity.noContent().build();
    }
}