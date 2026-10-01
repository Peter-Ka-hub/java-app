
package com.example.demo.api.subject;

import com.example.demo.api.common.NotFoundException;
import com.example.demo.api.generated.api.SubjectsApi;
import com.example.demo.api.generated.model.SubjectPageResponse;
import com.example.demo.api.generated.model.SubjectRequest;
import com.example.demo.api.generated.model.SubjectResponse;
import com.example.demo.domain.subject.Subject;
import com.example.demo.domain.subject.SubjectRepository;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRole;
import com.example.demo.infrastructure.ports.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class SubjectController implements SubjectsApi {

    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;
    private final SubjectMapper subjectMapper;

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<SubjectPageResponse> findAllSubjects(Integer page, Integer size) {
        Page<Subject> result = subjectRepository.findAll(PageRequest.of(page, size));
        return ResponseEntity.ok(new SubjectPageResponse()
                .items(subjectMapper.toResponseList(result.getContent()))
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages()));
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<SubjectResponse> getSubjectById(UUID id) {
        return ResponseEntity.ok(subjectMapper.toResponse(findOrThrow(id)));
    }

    @Override
    @Transactional
    public ResponseEntity<SubjectResponse> createSubject(SubjectRequest subjectRequest) {
        Subject subject = new Subject();
        apply(subjectRequest, subject);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subjectMapper.toResponse(subjectRepository.save(subject)));
    }

    @Override
    @Transactional
    public ResponseEntity<SubjectResponse> updateSubject(UUID id, SubjectRequest subjectRequest) {
        Subject subject = findOrThrow(id);
        apply(subjectRequest, subject);
        return ResponseEntity.ok(subjectMapper.toResponse(subject));
    }

    @Override
    @Transactional
    public ResponseEntity<Void> deleteSubject(UUID id) {
        subjectRepository.delete(findOrThrow(id));
        return ResponseEntity.noContent().build();
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

    private Subject findOrThrow(UUID id) {
        return subjectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Subject not found: " + id));
    }
}