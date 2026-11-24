package dev.schooldiary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MvcResult;

class SecurityApiTests extends ApiTestSupport {
  @Test
  void publicDocumentationHealthAndPingAreAccessibleWithoutLogin() throws Exception {
    for (String path :
        new String[] {
          "/api/ping", "/actuator/health", "/openapi.json", "/v3/api-docs/swagger-config"
        }) {
      mvc.perform(get(path).with(anonymous())).andExpect(status().isOk());
    }
  }

  @Test
  void privateEndpointsRequireAuthentication() throws Exception {
    for (String path : new String[] {"/api/users", "/api/schools", "/api/grades", "/api/auth/me"}) {
      mvc.perform(get(path).with(anonymous())).andExpect(status().isUnauthorized());
    }
  }

  @Test
  void unsafeRequestsRequireAValidCsrfToken() throws Exception {
    String request = objectMapper.writeValueAsString(schoolRequest());
    mvc.perform(post("/api/schools").contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isForbidden())
        .andExpect(
            jsonPath("$.message")
                .value(org.hamcrest.Matchers.containsString("CSRF token missing")));
    mvc.perform(
            post("/api/schools")
                .with(csrf().useInvalidToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isForbidden())
        .andExpect(
            jsonPath("$.message")
                .value(org.hamcrest.Matchers.containsString("Invalid CSRF token")));
    mvc.perform(
            post("/api/auth/login")
                .with(anonymous())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("email", ADMIN_EMAIL, "password", "Test-Admin-123!"))))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithAnonymousUser
  void realSessionLoginRotatesSessionAndLogoutRevokesAccess() throws Exception {
    var initial = csrfSession(null);
    String initialSessionId = initial.session().getId();

    MvcResult loggedIn =
        mvc.perform(
                post("/api/auth/login")
                    .with(anonymous())
                    .session(initial.session())
                    .header(initial.header(), initial.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("email", ADMIN_EMAIL, "password", "Test-Admin-123!"))))
            .andExpect(status().isOk())
            .andReturn();
    MockHttpSession session = (MockHttpSession) loggedIn.getRequest().getSession(false);
    assertThat(session).isNotNull();
    assertThat(session.getId()).isNotEqualTo(initialSessionId);
    // Subsequent requests must authenticate from the real session, without a mock principal.
    TestSecurityContextHolder.clearContext();

    mvc.perform(get("/api/auth/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
        .andExpect(jsonPath("$.password").doesNotExist())
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
    mvc.perform(get("/api/users").session(session)).andExpect(status().isOk());

    var authenticatedCsrf = csrfSession(session);
    mvc.perform(
            post("/api/auth/logout")
                .session(session)
                .header(authenticatedCsrf.header(), authenticatedCsrf.token()))
        .andExpect(status().isNoContent());
    assertThat(session.isInvalid()).isTrue();
    mvc.perform(get("/api/auth/me").with(anonymous())).andExpect(status().isUnauthorized());
  }

  @Test
  void wrongCredentialsAndUnknownEmailBothReturnUnauthorized() throws Exception {
    for (String email : new String[] {ADMIN_EMAIL, uniqueEmail()}) {
      mvc.perform(
              json(
                  post("/api/auth/login").with(anonymous()),
                  Map.of("email", email, "password", "Incorrect-password-123!")))
          .andExpect(status().isUnauthorized());
    }
  }

  @Test
  void passwordsAreWriteOnlyAndDisabledUsersCannotLogin() throws Exception {
    String school = createSchool();
    String email = uniqueEmail();
    String id = createUser("STUDENT", email, school);
    mvc.perform(get("/api/users/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.password").doesNotExist())
        .andExpect(jsonPath("$.passwordHash").doesNotExist());

    var update = withVersion("/api/users/" + id, userRequest("STUDENT", email, school));
    update.remove("password");
    update.put("enabled", false);
    mvc.perform(json(put("/api/users/{id}", id), update)).andExpect(status().isOk());
    mvc.perform(
            json(
                post("/api/auth/login").with(anonymous()),
                Map.of("email", email, "password", PASSWORD)))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/auth/me").with(actor(email, "STUDENT")))
        .andExpect(status().isForbidden());
  }

  @Test
  void acceptsAndAuthenticatesLongUnicodePasswordsWithoutTruncation() throws Exception {
    String school = createSchool();
    String email = uniqueEmail();
    String password = "Ż".repeat(128);
    var request = userRequest("STUDENT", email, school);
    request.put("password", password);
    create("/api/users", request);
    mvc.perform(
            json(
                post("/api/auth/login").with(anonymous()),
                Map.of("email", email, "password", password)))
        .andExpect(status().isOk());
    mvc.perform(
            json(
                post("/api/auth/login").with(anonymous()),
                Map.of("email", email, "password", password.substring(0, 127) + "!")))
        .andExpect(status().isUnauthorized());
    request.put("email", uniqueEmail());
    request.put("password", password + "!");
    mvc.perform(json(post("/api/users"), request)).andExpect(status().isBadRequest());
  }

  @Test
  void createUserRequiresAStrongPassword() throws Exception {
    String school = createSchool();
    var request = userRequest("STUDENT", uniqueEmail(), school);
    request.remove("password");
    mvc.perform(json(post("/api/users"), request)).andExpect(status().isBadRequest());
    request.put("password", "short");
    mvc.perform(json(post("/api/users"), request)).andExpect(status().isBadRequest());
    request.put("password", " ".repeat(12));
    mvc.perform(json(post("/api/users"), request)).andExpect(status().isBadRequest());
  }

  @Test
  void rejectsBlankPasswordUpdatesWithoutChangingTheExistingPassword() throws Exception {
    String school = createSchool();
    String email = uniqueEmail();
    String id = createUser("STUDENT", email, school);
    var update = withVersion("/api/users/" + id, userRequest("STUDENT", email, school));
    long originalVersion = ((Number) update.get("version")).longValue();
    update.put("password", " ".repeat(12));

    mvc.perform(json(put("/api/users/{id}", id), update)).andExpect(status().isBadRequest());
    mvc.perform(get("/api/users/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(originalVersion));
    mvc.perform(
            json(
                post("/api/auth/login").with(anonymous()),
                Map.of("email", email, "password", PASSWORD)))
        .andExpect(status().isOk());
  }

  @Test
  void studentCannotCreateAnAdministratorOrEditTheirRole() throws Exception {
    String school = createSchool();
    String email = uniqueEmail();
    String studentId = createUser("STUDENT", email, school);
    mvc.perform(
            json(
                post("/api/users").with(actor(email, "STUDENT")),
                userRequest("ADMIN", uniqueEmail(), school)))
        .andExpect(status().isForbidden());
    var roleChange = withVersion("/api/users/" + studentId, userRequest("ADMIN", email, school));
    mvc.perform(json(put("/api/users/{id}", studentId).with(actor(email, "STUDENT")), roleChange))
        .andExpect(status().isForbidden());
  }

  @Test
  void randomRecordIdsDoNotBypassAuthentication() throws Exception {
    mvc.perform(get("/api/grades/{id}", UUID.randomUUID()).with(anonymous()))
        .andExpect(status().isUnauthorized());
  }

  private CsrfSession csrfSession(MockHttpSession existing) throws Exception {
    var request = get("/api/auth/csrf");
    if (existing != null) {
      request.session(existing);
    } else {
      request.with(anonymous());
    }
    var result =
        mvc.perform(request)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.headerName").isNotEmpty())
            .andReturn();
    var body = response(result.getResponse().getContentAsString());
    return new CsrfSession(
        (MockHttpSession) result.getRequest().getSession(false),
        body.get("headerName").asText(),
        body.get("token").asText());
  }

  private record CsrfSession(MockHttpSession session, String header, String token) {}
}
