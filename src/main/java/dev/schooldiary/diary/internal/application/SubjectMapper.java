package dev.schooldiary.diary.internal.application;

import dev.schooldiary.diary.internal.domain.Subject;
import java.util.Set;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SubjectMapper {
  @Mapping(target = "id", source = "subject.id")
  @Mapping(target = "name", source = "subject.name")
  @Mapping(target = "teacherId", source = "subject.teacher.id")
  @Mapping(target = "schoolId", source = "subject.school.id")
  @Mapping(target = "academicYearId", source = "subject.academicYear.id")
  @Mapping(target = "classId", source = "subject.schoolClass.id")
  @Mapping(target = "version", source = "subject.version")
  @Mapping(target = "studentIds", source = "studentIds")
  SubjectView toView(Subject subject, Set<UUID> studentIds);
}
