package com.example.demo.api.school;

import com.example.demo.domain.school.School;
import com.example.demo.api.generated.model.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SchoolMapper {
    SchoolResponse toResponse(School school);
    List<SchoolResponse> toResponseList(List<School> schools);

    @Mapping(target = "id", ignore = true)
    School toEntity(SchoolRequest request);

    @Mapping(target = "id", ignore = true)
    void updateEntity(SchoolRequest request, @MappingTarget School school);
}