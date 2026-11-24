package com.example.demo.modules.subject.controller;

import com.example.demo.api.generated.model.SubjectResponse;
import com.example.demo.modules.subject.domain.Subject;
import com.example.demo.modules.user.domain.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface SubjectMapper {

    @Mapping(target = "teacherId", source = "teacher.id")
    @Mapping(target = "studentIds", source = "students")
    SubjectResponse toResponse(Subject subject);

    List<SubjectResponse> toResponseList(List<Subject> subjects);

    default Set<UUID> toIds(Set<User> users) {
        if (users == null) {
            return new LinkedHashSet<>();
        }
        return users.stream().map(User::getId).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}