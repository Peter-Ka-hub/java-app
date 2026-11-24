package dev.schooldiary;

import dev.schooldiary.identity.AccountCredentials;
import dev.schooldiary.identity.AccountPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

class AccountSecurityContextFactory implements WithSecurityContextFactory<WithAccount> {
  private final AccountCredentials credentials;

  @Autowired
  AccountSecurityContextFactory(AccountCredentials credentials) {
    this.credentials = credentials;
  }

  @Override
  public SecurityContext createSecurityContext(WithAccount annotation) {
    var principal =
        new AccountPrincipal(credentials.findCredentials(annotation.value()).orElseThrow());
    principal.eraseCredentials();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities()));
    return context;
  }
}
