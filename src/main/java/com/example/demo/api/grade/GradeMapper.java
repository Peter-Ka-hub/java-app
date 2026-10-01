package com.example.demo.api.grade;

import com.example.demo.api.generated.model.GradeResponse;
import com.example.demo.domain.grade.Grade;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface GradeMapper {

    @Mapping(target = "studentId", source = "student.id")
    @Mapping(target = "subjectId", source = "subject.id")
    GradeResponse toResponse(Grade grade);

    List<GradeResponse> toResponseList(List<Grade> grades);
}