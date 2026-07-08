package com.example.demo.api.school;

import com.example.demo.domain.school.School;
import com.example.model.SchoolRequest;
import com.example.model.SchoolResponse;
import org.mapstruct.Mapper;
import java.util.List;

@Mapper(componentModel = "spring")
public interface SchoolMapper {
    SchoolResponse toResponse(School school);
    List<SchoolResponse> toResponseList(List<School> schools);
    School toEntity(SchoolRequest request);
}