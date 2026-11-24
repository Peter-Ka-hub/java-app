package dev.schooldiary.identity.internal.authentication;

import dev.schooldiary.identity.AccountPrincipal;
import dev.schooldiary.identity.internal.persistence.UserRepository;
import dev.schooldiary.shared.access.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SessionActor implements CurrentActor {
  private final UserRepository users;

  @Override
  public Actor current() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken)
      throw new AuthenticationCredentialsNotFoundException("Authentication required");
    if (!(auth.getPrincipal() instanceof AccountPrincipal principal))
      throw new AuthenticationCredentialsNotFoundException("Sign in again to renew your session");
    var user =
        users
            .findById(principal.id())
            .orElseThrow(
                () ->
                    new AuthenticationCredentialsNotFoundException("Account no longer available"));
    if (!user.isEnabled()) throw new AccessDeniedException("Account disabled");
    if (user.getCredentialsVersion() != principal.credentialsVersion())
      throw new AuthenticationCredentialsNotFoundException("Credentials changed; sign in again");
    return new Actor(user.getId(), user.getRole(), user.schoolId());
  }
}
