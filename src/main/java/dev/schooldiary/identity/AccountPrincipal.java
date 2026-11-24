package dev.schooldiary.identity;

import java.io.Serial;
import java.util.Collection;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;

/** Session identity is bound to an immutable account ID and a revocable credential version. */
public final class AccountPrincipal implements UserDetails, CredentialsContainer {
  @Serial private static final long serialVersionUID = 1L;

  private final UUID id;
  private final String email;
  private final long credentialsVersion;
  private final boolean enabled;
  private final Collection<? extends GrantedAuthority> authorities;
  private String password;

  public AccountPrincipal(AccountCredentials.Credentials account) {
    id = account.id();
    email = account.email();
    credentialsVersion = account.credentialsVersion();
    enabled = account.enabled() && account.passwordHash() != null;
    authorities = AuthorityUtils.createAuthorityList("ROLE_" + account.role().name());
    password = account.passwordHash() == null ? "disabled-legacy-account" : account.passwordHash();
  }

  public UUID id() {
    return id;
  }

  public long credentialsVersion() {
    return credentialsVersion;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public String getPassword() {
    return password;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void eraseCredentials() {
    password = null;
  }
}
