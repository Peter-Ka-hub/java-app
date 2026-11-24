package com.example.demo.modules.school.service;

import com.example.demo.api.generated.model.SchoolRequest;
import com.example.demo.common.exception.NotFoundException;
import com.example.demo.modules.school.controller.SchoolMapper;
import com.example.demo.modules.school.domain.School;
import com.example.demo.modules.school.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SchoolService {

    private final SchoolRepository schoolRepository;
    private final SchoolMapper schoolMapper;

    @Transactional(readOnly = true)
    public Page<School> findAll(Pageable pageable) {
        return schoolRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public School findById(UUID id) {
        return schoolRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("School not found: " + id));
    }

    @Transactional
    public School create(SchoolRequest request) {
        School school = schoolMapper.toEntity(request);
        return schoolRepository.save(school);
    }

    @Transactional
    public School update(UUID id, SchoolRequest request) {
        School school = findById(id);
        schoolMapper.updateEntity(request, school);
        return school;
    }

    @Transactional
    public void delete(UUID id) {
        School school = findById(id);
        schoolRepository.delete(school);
    }
}