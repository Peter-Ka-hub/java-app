package com.example.demo.modules.user.controller;

import com.example.demo.common.exception.NotFoundException;
import com.example.demo.api.generated.api.UsersApi;
import com.example.demo.api.generated.model.UserPageResponse;
import com.example.demo.api.generated.model.UserRequest;
import com.example.demo.api.generated.model.UserResponse;
import com.example.demo.modules.school.domain.School;
import com.example.demo.modules.school.repository.SchoolRepository;
import com.example.demo.modules.user.domain.User;
import com.example.demo.modules.user.repository.UserRepository;
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
public class UserController implements UsersApi {

    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<UserPageResponse> findAllUsers(Integer page, Integer size) {
        Page<User> result = userRepository.findAll(PageRequest.of(page, size));
        return ResponseEntity.ok(new UserPageResponse()
                .items(userMapper.toResponseList(result.getContent()))
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages()));
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<UserResponse> getUserById(UUID id) {
        return ResponseEntity.ok(userMapper.toResponse(findOrThrow(id)));
    }

    @Override
    @Transactional
    public ResponseEntity<UserResponse> createUser(UserRequest userRequest) {
        User user = userMapper.toEntity(userRequest);
        user.setSchool(resolveSchool(userRequest.getSchoolId()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userMapper.toResponse(userRepository.save(user)));
    }

    @Override
    @Transactional
    public ResponseEntity<UserResponse> updateUser(UUID id, UserRequest userRequest) {
        User user = findOrThrow(id);
        userMapper.updateEntity(userRequest, user);
        user.setSchool(resolveSchool(userRequest.getSchoolId()));
        return ResponseEntity.ok(userMapper.toResponse(user));
    }

    @Override
    @Transactional
    public ResponseEntity<Void> deleteUser(UUID id) {
        userRepository.delete(findOrThrow(id));
        return ResponseEntity.noContent().build();
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
    }

    private School resolveSchool(UUID schoolId) {
        if (schoolId == null) {
            return null;
        }
        return schoolRepository.findById(schoolId)
                .orElseThrow(() -> new NotFoundException("School not found: " + schoolId));
    }
}