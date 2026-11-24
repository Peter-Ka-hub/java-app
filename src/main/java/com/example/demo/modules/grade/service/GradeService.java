package com.example.demo.modules.grade.service;

import com.example.demo.api.generated.model.GradeRequest;
import com.example.demo.common.exception.NotFoundException;
import com.example.demo.modules.grade.domain.Grade;
import com.example.demo.modules.grade.domain.Score;
import com.example.demo.modules.grade.repository.GradeRepository;
import com.example.demo.modules.subject.domain.Subject;
import com.example.demo.modules.subject.service.SubjectService;
import com.example.demo.modules.user.domain.User;
import com.example.demo.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GradeService {

    private final GradeRepository gradeRepository;
    private final UserService userService;
    private final SubjectService subjectService;

    @Transactional(readOnly = true)
    public Page<Grade> findAll(Pageable pageable) {
        return gradeRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Grade findById(UUID id) {
        return gradeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Grade not found: " + id));
    }

    @Transactional
    public Grade create(GradeRequest request) {
        Grade grade = new Grade();
        apply(request, grade);
        return gradeRepository.save(grade);
    }

    @Transactional
    public Grade update(UUID id, GradeRequest request) {
        Grade grade = findById(id);
        apply(request, grade);
        return grade;
    }

    @Transactional
    public void delete(UUID id) {
        Grade grade = findById(id);
        gradeRepository.delete(grade);
    }

    private void apply(GradeRequest request, Grade grade) {
        User student = userService.findById(request.getStudentId());
        Subject subject = subjectService.findById(request.getSubjectId());

        boolean isEnrolled = subject.getStudents().stream()
                .anyMatch(s -> s.getId().equals(student.getId()));

        if (!isEnrolled) {
            throw new IllegalArgumentException("Student " + student.getId() + " is not enrolled in subject " + subject.getId());
        }

        grade.setScore(Score.valueOf(request.getScore().name()));
        grade.setDateOfScore(request.getDateOfScore());
        grade.setStudent(student);
        grade.setSubject(subject);
    }
}