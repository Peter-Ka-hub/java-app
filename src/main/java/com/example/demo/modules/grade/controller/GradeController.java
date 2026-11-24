package com.example.demo.modules.grade.controller;

import com.example.demo.common.exception.NotFoundException;
import com.example.demo.api.generated.api.GradesApi;
import com.example.demo.api.generated.model.GradePageResponse;
import com.example.demo.api.generated.model.GradeRequest;
import com.example.demo.api.generated.model.GradeResponse;
import com.example.demo.modules.grade.domain.Grade;
import com.example.demo.modules.grade.domain.Score;
import com.example.demo.modules.grade.service.GradeService;
import com.example.demo.modules.subject.domain.Subject;
import com.example.demo.modules.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class GradeController implements GradesApi {

    GradeMapper gradeMapper;
    GradeService gradeService;

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<GradePageResponse> findAllGrades(int page, int size) {
        Page<Grade> result = gradeRepository.findAll(PageRequest.of(page, size));
        return ResponseEntity.ok(new GradePageResponse()
                .items(gradeMapper.toResponseList(result.getContent()))
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages()));
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<GradeResponse> getGradeById(UUID id) {
        return ResponseEntity.ok(gradeMapper.toResponse(findOrThrow(id)));
    }

    @Override
    @Transactional
    public ResponseEntity<GradeResponse> createGrade(GradeRequest gradeRequest) {
        var grade = gradeService.create(gradeRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(gradeMapper.toResponse(grade));
    }

    @Override
    @Transactional
    public ResponseEntity<GradeResponse> updateGrade(UUID id, GradeRequest gradeRequest) {
        Grade grade = findOrThrow(id);
        apply(gradeRequest, grade);
        return ResponseEntity.ok(gradeMapper.toResponse(grade));
    }

    @Override
    @Transactional
    public ResponseEntity<Void> deleteGrade(UUID id) {
        gradeRepository.delete(findOrThrow(id));
        return ResponseEntity.noContent().build();
    }

    private void apply(GradeRequest request, Grade grade) {
        User student = userRepository.findById(request.getStudentId())
                .orElseThrow(() -> new NotFoundException("Student not found: " + request.getStudentId()));
        Subject subject = subjectRepository.findById(request.getSubjectId())
                .orElseThrow(() -> new NotFoundException("Subject not found: " + request.getSubjectId()));
        if (!subject.getStudents().contains(student)) {
            throw new IllegalArgumentException("Student is not enrolled in subject " + subject.getId());
        }
        grade.setScore(Score.valueOf(request.getScore().name()));
        grade.setDateOfScore(request.getDateOfScore());
        grade.setStudent(student);
        grade.setSubject(subject);
    }

    private Grade findOrThrow(UUID id) {
        return gradeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Grade not found: " + id));
    }
}