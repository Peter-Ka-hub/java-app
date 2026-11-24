package com.example.demo.modules.user.service;

import com.example.demo.api.generated.model.UserRequest;
import com.example.demo.common.exception.NotFoundException;
import com.example.demo.modules.school.domain.School;
import com.example.demo.modules.school.service.SchoolService;
import com.example.demo.modules.user.controller.UserMapper;
import com.example.demo.modules.user.domain.User;
import com.example.demo.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final SchoolService schoolService;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public Page<User> findAll(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
    }

    @Transactional
    public User create(UserRequest request) {
        User user = userMapper.toEntity(request);
        user.setSchool(resolveSchool(request.getSchoolId()));
        return userRepository.save(user);
    }

    @Transactional
    public User update(UUID id, UserRequest request) {
        User user = findById(id);
        userMapper.updateEntity(request, user);
        user.setSchool(resolveSchool(request.getSchoolId()));
        return user;
    }

    @Transactional
    public void delete(UUID id) {
        User user = findById(id);
        userRepository.delete(user);
    }

    private School resolveSchool(UUID schoolId) {
        if (schoolId == null) {
            return null;
        }
        return schoolService.findById(schoolId);
    }
}