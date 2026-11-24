package dev.schooldiary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.schooldiary.identity.AccountPrincipal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** Exercises session identity through real authentication, not a mocked authenticated principal. */
class SessionSecurityRegressionTests extends ApiTestSupport {
  @Test
  void principalsFromTheOldEmailOnlySessionFormatMustSignInAgain() throws Exception {
    mvc.perform(get("/api/auth/me").with(user(ADMIN_EMAIL).roles("ADMIN")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void changingEmailAndReusingTheOldAddressCannotTransferAnExistingSession() throws Exception {
    String school = school();
    String originalEmail = uniqueEmail();
    var original = account("TEACHER", originalEmail, school);
    var session = login(originalEmail, PASSWORD);
    String replacementEmail = uniqueEmail();

    updateAccount(original.get("id").asText(), Map.of("email", replacementEmail));

    currentAccount(session)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(original.get("id").asText()))
        .andExpect(jsonPath("$.email").value(replacementEmail));

    var replacement = account("ADMIN", originalEmail, null);
    assertThat(replacement.get("id").asText()).isNotEqualTo(original.get("id").asText());
    currentAccount(session)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(original.get("id").asText()))
        .andExpect(jsonPath("$.role").value("TEACHER"))
        .andExpect(jsonPath("$.schoolId").value(school));
  }

  @Test
  void deletingAnAccountAndReusingItsEmailNeverReactivatesItsOldSession() throws Exception {
    String email = uniqueEmail();
    var original = account("TEACHER", email, school());
    var session = login(email, PASSWORD);

    mvc.perform(
            delete("/api/users/{id}", original.get("id").asText())
                .with(actor(ADMIN_EMAIL, "ADMIN"))
                .with(csrf()))
        .andExpect(status().isNoContent());
    currentAccount(session).andExpect(status().isUnauthorized());

    var replacement = account("ADMIN", email, null);
    assertThat(replacement.get("id").asText()).isNotEqualTo(original.get("id").asText());
    currentAccount(session).andExpect(status().isUnauthorized());
    currentAccount(login(email, PASSWORD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(replacement.get("id").asText()));
  }

  @Test
  void passwordResetRevokesEveryPreviousSessionAndTheNewPasswordAuthenticates() throws Exception {
    String email = uniqueEmail();
    var original = account("STUDENT", email, school());
    var firstSession = login(email, PASSWORD);
    var secondSession = login(email, PASSWORD);
    assertThat(secondSession.getId()).isNotEqualTo(firstSession.getId());
    String newPassword = "New-Student-Password-123!";

    updateAccount(original.get("id").asText(), Map.of("password", newPassword));

    currentAccount(firstSession).andExpect(status().isUnauthorized());
    currentAccount(secondSession).andExpect(status().isUnauthorized());
    authenticate(email, PASSWORD, 401);
    currentAccount(login(email, newPassword))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(original.get("id").asText()));
  }

  @Test
  void reenablingAnAccountDoesNotRestoreSessionsCreatedBeforeItWasDisabled() throws Exception {
    String email = uniqueEmail();
    var original = account("STUDENT", email, school());
    String id = original.get("id").asText();
    var session = login(email, PASSWORD);

    updateAccount(id, Map.of("enabled", false));

    currentAccount(session).andExpect(status().isForbidden());
    authenticate(email, PASSWORD, 401);

    updateAccount(id, Map.of("enabled", true));

    currentAccount(session).andExpect(status().isUnauthorized());
    currentAccount(login(email, PASSWORD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id));
  }

  @Test
  void ordinaryProfileChangesWithoutPasswordKeepAnExistingSessionUsable() throws Exception {
    String email = uniqueEmail();
    var original = account("STUDENT", email, school());
    var session = login(email, PASSWORD);

    updateAccount(original.get("id").asText(), Map.of("firstName", "Updated"));

    currentAccount(session)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(original.get("id").asText()))
        .andExpect(jsonPath("$.firstName").value("Updated"));
  }

  @ParameterizedTest(name = "change role rather than school: {0}")
  @ValueSource(booleans = {true, false})
  void changingPermissionsRequiresANewSession(boolean changeRole) throws Exception {
    String email = uniqueEmail();
    var original = account("TEACHER", email, school());
    var session = login(email, PASSWORD);
    Map<String, Object> change =
        changeRole ? Map.of("role", "STUDENT") : Map.of("schoolId", school());

    updateAccount(original.get("id").asText(), change);

    currentAccount(session).andExpect(status().isUnauthorized());
    var fresh =
        currentAccount(login(email, PASSWORD))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(original.get("id").asText()));
    if (changeRole) fresh.andExpect(jsonPath("$.role").value("STUDENT"));
    else fresh.andExpect(jsonPath("$.schoolId").value(change.get("schoolId")));
  }

  private String school() throws Exception {
    return createAsAdmin("/api/schools", schoolRequest()).get("id").asText();
  }

  private JsonNode account(String role, String email, String school) throws Exception {
    return createAsAdmin("/api/users", userRequest(role, email, school));
  }

  private JsonNode createAsAdmin(String path, Object body) throws Exception {
    return response(
        mvc.perform(json(post(path).with(actor(ADMIN_EMAIL, "ADMIN")), body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private void updateAccount(String id, Map<String, Object> changes) throws Exception {
    var current =
        response(
            mvc.perform(get("/api/users/{id}", id).with(actor(ADMIN_EMAIL, "ADMIN")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    Map<String, Object> request = new LinkedHashMap<>();
    for (String field : new String[] {"firstName", "lastName", "email", "role", "dateOfBirth"})
      request.put(field, current.get(field).asText());
    if (current.hasNonNull("schoolId")) request.put("schoolId", current.get("schoolId").asText());
    request.put("enabled", current.get("enabled").asBoolean());
    request.put("version", current.get("version").asLong());
    request.putAll(changes);
    mvc.perform(json(put("/api/users/{id}", id).with(actor(ADMIN_EMAIL, "ADMIN")), request))
        .andExpect(status().isOk());
  }

  private MockHttpSession login(String email, String password) throws Exception {
    var result = authenticate(email, password, 200);
    var session = (MockHttpSession) result.getRequest().getSession(false);
    assertThat(session).isNotNull();
    var context =
        (SecurityContext)
            session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    assertThat(context.getAuthentication().getPrincipal()).isInstanceOf(AccountPrincipal.class);
    var principal = (AccountPrincipal) context.getAuthentication().getPrincipal();
    assertThat(principal.id()).isNotNull();
    assertThat(principal.getPassword())
        .as("password hashes must be erased before session storage")
        .isNull();
    TestSecurityContextHolder.clearContext();
    return session;
  }

  private MvcResult authenticate(String email, String password, int expectedStatus)
      throws Exception {
    var csrf = csrfSession();
    TestSecurityContextHolder.clearContext();
    return mvc.perform(
            post("/api/auth/login")
                .with(anonymous())
                .session(csrf.session())
                .header(csrf.header(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
        .andExpect(status().is(expectedStatus))
        .andReturn();
  }

  private CsrfSession csrfSession() throws Exception {
    TestSecurityContextHolder.clearContext();
    var result =
        mvc.perform(get("/api/auth/csrf").with(anonymous())).andExpect(status().isOk()).andReturn();
    var body = response(result.getResponse().getContentAsString());
    return new CsrfSession(
        (MockHttpSession) result.getRequest().getSession(false),
        body.get("headerName").asText(),
        body.get("token").asText());
  }

  private org.springframework.test.web.servlet.ResultActions currentAccount(MockHttpSession session)
      throws Exception {
    TestSecurityContextHolder.clearContext();
    return mvc.perform(get("/api/auth/me").session(session));
  }

  private record CsrfSession(MockHttpSession session, String header, String token) {}
}
