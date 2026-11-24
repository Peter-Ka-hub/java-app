package dev.schooldiary.identity.internal.web;

import dev.schooldiary.generated.model.*;
import dev.schooldiary.identity.internal.application.*;
import dev.schooldiary.shared.application.PageResult;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserHttpMapper {
  UserCommand toCommand(UserCreateRequest request);

  UserCommand toCommand(UserUpdateRequest request);

  UserResponse toResponse(UserView view);

  UserPageResponse toResponse(PageResult<UserView> page);
}
