package com.example.demo.modules.subject.service;

import com.example.demo.api.generated.model.SubjectRequest;
import com.example.demo.common.exception.NotFoundException;
import com.example.demo.modules.subject.domain.Subject;
import com.example.demo.modules.subject.repository.SubjectRepository;
import com.example.demo.modules.user.domain.User;
import com.example.demo.modules.user.domain.UserRole;
import com.example.demo.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<Subject> findAll(Pageable pageable) {
        return subjectRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Subject findById(UUID id) {
        return subjectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Subject not found: " + id));
    }

    @Transactional
    public Subject create(SubjectRequest request) {
        Subject subject = new Subject();
        apply(request, subject);
        return subjectRepository.save(subject);
    }

    @Transactional
    public Subject update(UUID id, SubjectRequest request) {
        Subject subject = findById(id);
        apply(request, subject);
        return subject;
    }

    @Transactional
    public void delete(UUID id) {
        Subject subject = findById(id);
        subjectRepository.delete(subject);
    }

    private void apply(SubjectRequest request, Subject subject) {
        User teacher = userRepository.findById(request.getTeacherId())
                .orElseThrow(() -> new NotFoundException("Teacher not found: " + request.getTeacherId()));
        if (teacher.getRole() != UserRole.TEACHER) {
            throw new IllegalArgumentException("User " + teacher.getId() + " is not a TEACHER");
        }

        List<User> students = request.getStudentIds() == null
                ? List.of()
                : userRepository.findAllById(request.getStudentIds());

        if (request.getStudentIds() != null && students.size() != request.getStudentIds().size()) {
            throw new NotFoundException("Some students do not exist");
        }

        subject.setName(request.getName());
        subject.setTeacher(teacher);
        subject.setStudents(new HashSet<>(students));
    }
}