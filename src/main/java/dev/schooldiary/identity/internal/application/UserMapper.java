package dev.schooldiary.identity.internal.application;

import dev.schooldiary.identity.internal.domain.User;
import java.util.List;
import java.util.Locale;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface UserMapper {

  @Mapping(target = "schoolId", source = "school.id")
  UserView toView(User user);

  List<UserView> toViewList(List<User> users);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "pesel", ignore = true)
  @Mapping(target = "school", ignore = true)
  @Mapping(target = "passwordHash", ignore = true)
  @Mapping(target = "credentialsVersion", ignore = true)
  @Mapping(target = "enabled", ignore = true)
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "email", source = "email", qualifiedByName = "normalizeEmail")
  User toEntity(UserCommand request);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "pesel", ignore = true)
  @Mapping(target = "school", ignore = true)
  @Mapping(target = "passwordHash", ignore = true)
  @Mapping(target = "credentialsVersion", ignore = true)
  @Mapping(target = "enabled", ignore = true)
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "email", source = "email", qualifiedByName = "normalizeEmail")
  void updateEntity(UserCommand request, @MappingTarget User user);

  @Named("normalizeEmail")
  default String normalizeEmail(String email) {
    return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
  }
}
