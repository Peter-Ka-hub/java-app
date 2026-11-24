package dev.schooldiary.platform.security;

import dev.schooldiary.identity.AccountCredentials;
import dev.schooldiary.identity.AccountPrincipal;
import dev.schooldiary.platform.web.ApiErrorResponse;
import dev.schooldiary.shared.access.CurrentActor;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import tools.jackson.databind.ObjectMapper;

@Configuration
@RequiredArgsConstructor
public class SecurityConfiguration {
  private final ObjectMapper json;

  @Bean
  PasswordEncoder passwordEncoder() {
    String current = "pbkdf2@SpringSecurity_v5_8";
    return new DelegatingPasswordEncoder(
        current,
        Map.of(
            current,
            Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
            "bcrypt",
            new BCryptPasswordEncoder()));
  }

  @Bean
  UserDetailsService userDetailsService(AccountCredentials users) {
    return email -> {
      var user =
          users
              .findCredentials(email.strip())
              .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
      return new AccountPrincipal(user);
    };
  }

  @Bean
  AuthenticationManager authenticationManager(UserDetailsService details, PasswordEncoder encoder) {
    var provider = new DaoAuthenticationProvider(details);
    provider.setPasswordEncoder(encoder);
    return new ProviderManager(provider);
  }

  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  HttpSessionCsrfTokenRepository csrfTokenRepository() {
    return new HttpSessionCsrfTokenRepository();
  }

  @Bean
  SessionAuthenticationStrategy sessionAuthenticationStrategy(HttpSessionCsrfTokenRepository csrf) {
    return new CompositeSessionAuthenticationStrategy(
        List.of(new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf)));
  }

  @Bean
  SecurityFilterChain filterChain(
      HttpSecurity http,
      SecurityContextRepository repository,
      HttpSessionCsrfTokenRepository csrf,
      CurrentActor access,
      Environment environment,
      @Value("${app.security.login.max-attempts:10}") int maxAttempts,
      @Value("${app.security.login.window:1m}") Duration window,
      @Value("${app.security.login.max-entries:10000}") int maxEntries)
      throws Exception {
    boolean dev = environment.acceptsProfiles(Profiles.of("dev"));
    http.authorizeHttpRequests(
            auth -> {
              String[] frontend = {"/", "/index.html", "/ui/**", "/favicon.svg"};
              auth.requestMatchers(HttpMethod.GET, frontend).permitAll();
              auth.requestMatchers(HttpMethod.HEAD, frontend).permitAll();
              auth.requestMatchers(HttpMethod.POST, "/api/invitations/accept").permitAll();
              auth.requestMatchers(
                      "/api/auth/login",
                      "/api/auth/csrf",
                      "/api/ping",
                      "/openapi.json",
                      "/swagger-ui.html",
                      "/swagger-ui/**",
                      "/v3/api-docs/**",
                      "/actuator/health",
                      "/actuator/health/**",
                      "/error")
                  .permitAll();
              if (dev) {
                auth.requestMatchers("/h2-console", "/h2-console/**")
                    .access(
                        (authentication, context) ->
                            new AuthorizationDecision(isCurrentAdmin(access, true)));
              }
              auth.requestMatchers("/actuator/**")
                  .access(
                      (authentication, context) ->
                          new AuthorizationDecision(isCurrentAdmin(access, false)))
                  .anyRequest()
                  .authenticated();
            })
        .csrf(
            config -> {
              config.csrfTokenRepository(csrf);
              if (dev) {
                config.ignoringRequestMatchers("/h2-console", "/h2-console/**");
              }
            })
        .addFilterAfter(
            new LoginRateLimitFilter(json, maxAttempts, window, maxEntries), CsrfFilter.class)
        .securityContext(config -> config.securityContextRepository(repository))
        .requestCache(config -> config.disable())
        .exceptionHandling(
            config ->
                config
                    .authenticationEntryPoint(
                        (req, res, ex) -> {
                          res.setStatus(401);
                          res.setContentType("application/json");
                          json.writeValue(
                              res.getOutputStream(),
                              new ApiErrorResponse(
                                  Instant.now(),
                                  401,
                                  "Unauthorized",
                                  "Authentication required",
                                  req.getRequestURI(),
                                  List.of()));
                        })
                    .accessDeniedHandler(
                        (req, res, ex) -> {
                          res.setStatus(403);
                          res.setContentType("application/json");
                          json.writeValue(
                              res.getOutputStream(),
                              new ApiErrorResponse(
                                  Instant.now(),
                                  403,
                                  "Forbidden",
                                  csrfMessage(ex),
                                  req.getRequestURI(),
                                  List.of()));
                        }))
        .logout(
            config ->
                config
                    .logoutUrl("/api/auth/logout")
                    .deleteCookies("JSESSIONID")
                    .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
        .headers(
            headers -> {
              if (dev) {
                headers.frameOptions(frames -> frames.sameOrigin());
              }
              headers.contentSecurityPolicy(
                  csp ->
                      csp.policyDirectives(
                          "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self'"
                              + " 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'self'"));
            });
    return http.build();
  }

  private boolean isCurrentAdmin(CurrentActor access, boolean globalOnly) {
    try {
      if (globalOnly) {
        access.requireGlobalAdmin();
      } else {
        access.requireAdmin();
      }
      return true;
    } catch (AuthenticationException | AccessDeniedException ex) {
      return false;
    }
  }

  private static String csrfMessage(AccessDeniedException exception) {
    if (exception instanceof MissingCsrfTokenException)
      return "CSRF token missing or session expired. Fetch /api/auth/csrf and retry.";
    if (exception instanceof InvalidCsrfTokenException)
      return "Invalid CSRF token. Fetch /api/auth/csrf again after login and retry.";
    return "Access denied";
  }
}
