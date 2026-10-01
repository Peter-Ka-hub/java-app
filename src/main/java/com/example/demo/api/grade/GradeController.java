package com.example.demo.api.grade;

import com.example.demo.api.common.NotFoundException;
import com.example.demo.api.generated.api.GradesApi;
import com.example.demo.api.generated.model.GradePageResponse;
import com.example.demo.api.generated.model.GradeRequest;
import com.example.demo.api.generated.model.GradeResponse;
import com.example.demo.domain.grade.Grade;
import com.example.demo.domain.grade.GradeRepository;
import com.example.demo.domain.grade.Score;
import com.example.demo.domain.subject.Subject;
import com.example.demo.domain.subject.SubjectRepository;
import com.example.demo.domain.user.User;
import com.example.demo.infrastructure.ports.UserRepository;
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
public class GradeController implements GradesApi {

    private final GradeRepository gradeRepository;
    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;
    private final GradeMapper gradeMapper;

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<GradePageResponse> findAllGrades(Integer page, Integer size) {
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
        Grade grade = new Grade();
        apply(gradeRequest, grade);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(gradeMapper.toResponse(gradeRepository.save(grade)));
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