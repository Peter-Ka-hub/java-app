package com.example.demo.api.user;

import com.example.demo.api.generated.model.UserRequest;
import com.example.demo.api.generated.model.UserResponse;
import com.example.demo.domain.user.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.util.List;
import java.util.Locale;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "schoolId", source = "school.id")
    UserResponse toResponse(User user);

    List<UserResponse> toResponseList(List<User> users);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "pesel", ignore = true)
    @Mapping(target = "school", ignore = true)
    @Mapping(target = "email", source = "email", qualifiedByName = "normalizeEmail")
    User toEntity(UserRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "pesel", ignore = true)
    @Mapping(target = "school", ignore = true)
    @Mapping(target = "email", source = "email", qualifiedByName = "normalizeEmail")
    void updateEntity(UserRequest request, @MappingTarget User user);

    @Named("normalizeEmail")
    default String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}