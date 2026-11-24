package dev.schooldiary.diary.internal.application;

import dev.schooldiary.diary.internal.domain.Grade;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface GradeMapper {
  @Mapping(target = "studentId", source = "student.id")
  @Mapping(target = "subjectId", source = "subject.id")
  @Mapping(target = "gradedById", source = "gradedBy.id")
  GradeView toView(Grade grade);
}
