package com.example.demo.api.school;

import com.example.demo.api.common.NotFoundException;
import com.example.demo.api.generated.api.SchoolsApi;
import com.example.demo.api.generated.model.SchoolPageResponse;
import com.example.demo.api.generated.model.SchoolRequest;
import com.example.demo.api.generated.model.SchoolResponse;
import com.example.demo.domain.school.School;
import com.example.demo.domain.school.SchoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class SchoolController implements SchoolsApi {

    private final SchoolRepository schoolRepository;
    private final SchoolMapper schoolMapper;

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<SchoolPageResponse> findAllSchools(Integer page, Integer size) {
        Page<School> result = schoolRepository.findAll(PageRequest.of(page, size));
        return ResponseEntity.ok(new SchoolPageResponse()
                .items(schoolMapper.toResponseList(result.getContent()))
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages()));
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<SchoolResponse> getSchoolById(UUID id) {
        return ResponseEntity.ok(schoolMapper.toResponse(findOrThrow(id)));
    }

    @Override
    @Transactional
    public ResponseEntity<SchoolResponse> createSchool(SchoolRequest schoolRequest) {
        School saved = schoolRepository.save(schoolMapper.toEntity(schoolRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(schoolMapper.toResponse(saved));
    }

    @Override
    @Transactional
    public ResponseEntity<SchoolResponse> updateSchool(UUID id, SchoolRequest schoolRequest) {
        School school = findOrThrow(id);
        schoolMapper.updateEntity(schoolRequest, school);
        return ResponseEntity.ok(schoolMapper.toResponse(school));
    }

    @Override
    @Transactional
    public ResponseEntity<Void> deleteSchool(UUID id) {
        schoolRepository.delete(findOrThrow(id));
        return ResponseEntity.noContent().build();
    }

    private School findOrThrow(UUID id) {
        return schoolRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("School not found: " + id));
    }
}