package com.example.demo.modules.school.controller;

import com.example.demo.api.generated.model.SchoolResponse;
import com.example.demo.modules.school.domain.School;
import com.example.demo.api.generated.model.*;
import jakarta.validation.Valid;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SchoolMapper {
    SchoolResponse toResponse(School school);
    List<@Valid SchoolResponse> toResponseList(List<School> schools);

    @Mapping(target = "id", ignore = true)
    School toEntity(SchoolRequest request);

    @Mapping(target = "id", ignore = true)
    void updateEntity(SchoolRequest request, @MappingTarget School school);
}