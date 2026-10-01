package com.example.demo.api.user;

import com.example.demo.api.generated.model.UserRequest;
import com.example.demo.api.generated.model.UserResponse;
import com.example.demo.domain.user.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "schoolId", source = "school.id")
    UserResponse toResponse(User user);

    List<UserResponse> toResponseList(List<User> users);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "pesel", ignore = true)
    @Mapping(target = "school", ignore = true)
    User toEntity(UserRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "pesel", ignore = true)
    @Mapping(target = "school", ignore = true)
    void updateEntity(UserRequest request, @MappingTarget User user);
}