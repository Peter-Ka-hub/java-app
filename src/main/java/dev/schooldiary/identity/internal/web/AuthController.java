package dev.schooldiary.identity.internal.web;

import dev.schooldiary.generated.model.LoginRequest;
import dev.schooldiary.generated.model.UserResponse;
import dev.schooldiary.identity.internal.application.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
  private final AuthenticationManager authentication;
  private final SecurityContextRepository contexts;
  private final SessionAuthenticationStrategy sessions;
  private final UserService users;
  private final UserHttpMapper mapper;

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of(
        "token",
        token.getToken(),
        "headerName",
        token.getHeaderName(),
        "parameterName",
        token.getParameterName());
  }

  @PostMapping("/login")
  public UserResponse login(
      @Valid @RequestBody LoginRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    var result =
        authentication.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(
                body.getEmail().strip(), body.getPassword()));
    sessions.onAuthentication(result, request, response);
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(result);
    SecurityContextHolder.setContext(context);
    contexts.saveContext(context, request, response);
    return mapper.toResponse(users.me());
  }

  @GetMapping("/me")
  public UserResponse me() {
    return mapper.toResponse(users.me());
  }
}
