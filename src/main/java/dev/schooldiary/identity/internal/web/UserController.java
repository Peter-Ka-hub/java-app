package dev.schooldiary.identity.internal.web;

import dev.schooldiary.generated.api.UsersApi;
import dev.schooldiary.generated.model.*;
import dev.schooldiary.identity.internal.application.UserService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController implements UsersApi {
  private final UserService service;
  private final UserHttpMapper mapper;

  @Override
  public ResponseEntity<UserPageResponse> findAllUsers(Integer page, Integer size) {
    return ResponseEntity.ok(mapper.toResponse(service.list(page, size)));
  }

  @Override
  public ResponseEntity<UserResponse> getUserById(UUID id) {
    return ResponseEntity.ok(mapper.toResponse(service.get(id)));
  }

  @Override
  public ResponseEntity<UserResponse> createUser(UserCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.toResponse(service.create(mapper.toCommand(request))));
  }

  @Override
  public ResponseEntity<UserResponse> updateUser(UUID id, UserUpdateRequest request) {
    return ResponseEntity.ok(mapper.toResponse(service.update(id, mapper.toCommand(request))));
  }

  @Override
  public ResponseEntity<Void> deleteUser(UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
