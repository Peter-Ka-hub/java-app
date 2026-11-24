package dev.schooldiary.organization.internal.application;

import dev.schooldiary.organization.internal.domain.School;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface SchoolMapper {
  SchoolView toView(School school);

  List<SchoolView> toViewList(List<School> schools);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "version", ignore = true)
  School toEntity(SchoolCommand request);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "version", ignore = true)
  void updateEntity(SchoolCommand request, @MappingTarget School school);
}
