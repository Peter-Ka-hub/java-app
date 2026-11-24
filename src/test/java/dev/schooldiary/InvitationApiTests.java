package dev.schooldiary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.TestSecurityContextHolder;
import tools.jackson.databind.JsonNode;

class InvitationApiTests extends ApiTestSupport {
  private static final String INVITATIONS = "/api/invitations";
  private static final String ACCEPT = INVITATIONS + "/accept";
  private static final String INVITED_PASSWORD = "Invited-Test-12345!";

  @Autowired JdbcTemplate jdbc;

  @ParameterizedTest
  @ValueSource(strings = {"STUDENT", "TEACHER"})
  void invitedAccountIsCreatedOnceAndCanSignInThroughTheExistingSessionFlow(String role)
      throws Exception {
    String schoolId = createSchool();
    String adminEmail = uniqueEmail();
    createUser("ADMIN", adminEmail, schoolId);
    var schoolAdmin = actor(adminEmail, "ADMIN");
    String email = uniqueEmail();
    Instant beforeCreate = Instant.now();
    var created =
        response(
            mvc.perform(
                    json(
                        post(INVITATIONS).with(schoolAdmin),
                        Map.of("email", email.toUpperCase(Locale.ROOT), "role", role)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitation.email").value(email))
                .andExpect(jsonPath("$.invitation.schoolId").value(schoolId))
                .andExpect(jsonPath("$.invitation.role").value(role))
                .andExpect(jsonPath("$.invitation.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString());
    var invitation = created.get("invitation");
    var id = UUID.fromString(invitation.get("id").asText());
    var token = created.get("token").asText();
    Instant expiresAt = OffsetDateTime.parse(invitation.get("expiresAt").asText()).toInstant();
    Duration lifetime = Duration.ofHours(24);
    assertThat(expiresAt).isBetween(beforeCreate.plus(lifetime), Instant.now().plus(lifetime));
    assertThat(token).matches("[A-Za-z0-9_-]{43}");
    String digest =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
    assertThat(
            jdbc.queryForObject(
                "select token_hash from account_invitation where id = ?", String.class, id))
        .isEqualTo(digest);
    assertThat(credentials.findCredentials(email)).isEmpty();

    var csrfResult =
        mvc.perform(get("/api/auth/csrf").with(anonymous())).andExpect(status().isOk()).andReturn();
    var csrfBody = response(csrfResult.getResponse().getContentAsString());
    var session = (MockHttpSession) csrfResult.getRequest().getSession(false);
    String csrfHeader = csrfBody.get("headerName").asText();
    String csrfToken = csrfBody.get("token").asText();
    mvc.perform(
            post(ACCEPT)
                .with(anonymous())
                .session(session)
                .header(csrfHeader, csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(acceptance(token))))
        .andExpect(status().isNoContent());

    mvc.perform(get(INVITATIONS).with(schoolAdmin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].id").value(id.toString()))
        .andExpect(jsonPath("$.items[0].status").value("ACCEPTED"))
        .andExpect(jsonPath("$.items[0].token").doesNotExist())
        .andExpect(jsonPath("$.items[0].tokenHash").doesNotExist());
    var repeated =
        mvc.perform(json(post(ACCEPT).with(anonymous()), acceptance(token)))
            .andExpect(status().isNotFound())
            .andReturn();
    assertThat(repeated.getResponse().getContentAsString()).doesNotContain(token, INVITED_PASSWORD);
    assertThat(credentials.findCredentials(email).orElseThrow().passwordHash())
        .startsWith("{pbkdf2@SpringSecurity_v5_8}")
        .doesNotContain(INVITED_PASSWORD);

    TestSecurityContextHolder.clearContext();
    mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    var login =
        mvc.perform(
                post("/api/auth/login")
                    .session(session)
                    .header(csrfHeader, csrfToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("email", email, "password", INVITED_PASSWORD))))
            .andExpect(status().isOk())
            .andReturn();
    mvc.perform(get("/api/auth/me").session((MockHttpSession) login.getRequest().getSession(false)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.role").value(role))
        .andExpect(jsonPath("$.schoolId").value(schoolId))
        .andExpect(jsonPath("$.firstName").value("Alicja"))
        .andExpect(jsonPath("$.lastName").value("Nowak"))
        .andExpect(jsonPath("$.dateOfBirth").value("2000-01-01"))
        .andExpect(jsonPath("$.password").doesNotExist())
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }

  @ParameterizedTest
  @ValueSource(strings = {"EXPIRED", "REVOKED"})
  void expiredAndRevokedInvitationsNeverCreateAnAccount(String terminalStatus) throws Exception {
    String schoolId = createSchool();
    String adminEmail = uniqueEmail();
    createUser("ADMIN", adminEmail, schoolId);
    String email = uniqueEmail();
    var created = invite(email, schoolId);
    String id = created.get("invitation").get("id").asText();
    var schoolAdmin = actor(adminEmail, "ADMIN");

    if ("EXPIRED".equals(terminalStatus)) {
      jdbc.update(
          "update account_invitation set expires_at = ? where id = ?",
          OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),
          UUID.fromString(id));
    } else {
      mvc.perform(post(INVITATIONS + "/{id}/revoke", id).with(schoolAdmin).with(csrf()))
          .andExpect(status().isNoContent());
    }

    mvc.perform(get(INVITATIONS).with(schoolAdmin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(id))
        .andExpect(jsonPath("$.items[0].status").value(terminalStatus));
    mvc.perform(json(post(ACCEPT).with(anonymous()), acceptance(created.get("token").asText())))
        .andExpect(status().isNotFound());
    mvc.perform(post(INVITATIONS + "/{id}/revoke", id).with(schoolAdmin).with(csrf()))
        .andExpect(status().isConflict());
    assertThat(credentials.findCredentials(email)).isEmpty();
  }

  @Test
  void schoolAdministratorCanOnlyCreateListAndRevokeInvitationsInTheirSchool() throws Exception {
    String schoolId = createSchool();
    String otherSchool = createSchool();
    String adminEmail = uniqueEmail();
    createUser("ADMIN", adminEmail, schoolId);
    var schoolAdmin = actor(adminEmail, "ADMIN");
    var foreign = invite(uniqueEmail(), otherSchool);
    var own = invite(uniqueEmail(), schoolId);

    mvc.perform(
            json(
                post(INVITATIONS).with(schoolAdmin),
                Map.of("email", uniqueEmail(), "role", "STUDENT", "schoolId", otherSchool)))
        .andExpect(status().isForbidden());
    mvc.perform(get(INVITATIONS).with(schoolAdmin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].id").value(own.get("invitation").get("id").asText()));
    mvc.perform(
            post(INVITATIONS + "/{id}/revoke", foreign.get("invitation").get("id").asText())
                .with(schoolAdmin)
                .with(csrf()))
        .andExpect(status().isNotFound());
    mvc.perform(json(post(ACCEPT).with(anonymous()), acceptance(foreign.get("token").asText())))
        .andExpect(status().isNoContent());
  }

  @Test
  void globalAdministratorMustChooseASchoolAndCannotInviteAnAdministrator() throws Exception {
    String schoolId = createSchool();
    mvc.perform(json(post(INVITATIONS), Map.of("email", uniqueEmail(), "role", "STUDENT")))
        .andExpect(status().isBadRequest());
    mvc.perform(
            json(
                post(INVITATIONS),
                Map.of("email", uniqueEmail(), "role", "ADMIN", "schoolId", schoolId)))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @ValueSource(strings = {"STUDENT", "TEACHER"})
  void nonAdministratorsCannotManageInvitationsEvenInTheirOwnSchool(String role) throws Exception {
    String schoolId = createSchool();
    String email = uniqueEmail();
    createUser(role, email, schoolId);
    var nonAdmin = actor(email, role);
    var invitation = invite(uniqueEmail(), schoolId).get("invitation");

    mvc.perform(get(INVITATIONS).with(nonAdmin)).andExpect(status().isForbidden());
    mvc.perform(
            json(
                post(INVITATIONS).with(nonAdmin),
                Map.of("email", uniqueEmail(), "role", "STUDENT", "schoolId", schoolId)))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(INVITATIONS + "/{id}/revoke", invitation.get("id").asText())
                .with(nonAdmin)
                .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  void acceptanceRejectsMissingCsrfAndOverpostedAccountFieldsWithoutConsumingTheLink()
      throws Exception {
    String schoolId = createSchool();
    String email = uniqueEmail();
    var invitation = invite(email, schoolId);
    var body = acceptance(invitation.get("token").asText());
    mvc.perform(
            post(ACCEPT)
                .with(anonymous())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isForbidden());

    var overrides = Map.of("role", "ADMIN", "email", uniqueEmail(), "schoolId", createSchool());
    for (var override : overrides.entrySet()) {
      var tampered = new LinkedHashMap<>(body);
      tampered.put(override.getKey(), override.getValue());
      mvc.perform(json(post(ACCEPT).with(anonymous()), tampered))
          .andExpect(status().isBadRequest());
    }
    assertThat(credentials.findCredentials(email)).isEmpty();
    mvc.perform(json(post(ACCEPT).with(anonymous()), body)).andExpect(status().isNoContent());
    var account = credentials.findCredentials(email).orElseThrow();
    var user = read("/api/users/" + account.id());
    assertThat(user.get("schoolId").asText()).isEqualTo(schoolId);
    assertThat(user.get("role").asText()).isEqualTo("STUDENT");
  }

  @Test
  void anAccountCreatedAfterIssuingTheLinkKeepsItsProfileCredentialsAndPermissions()
      throws Exception {
    String email = uniqueEmail();
    var invitation = invite(email, createSchool());
    String accountPath = "/api/users/" + createUser("TEACHER", email, createSchool());
    var before = read(accountPath);
    var originalCredentials = credentials.findCredentials(email).orElseThrow();

    mvc.perform(json(post(ACCEPT).with(anonymous()), acceptance(invitation.get("token").asText())))
        .andExpect(status().isConflict());

    assertThat(read(accountPath)).isEqualTo(before);
    assertThat(credentials.findCredentials(email)).contains(originalCredentials);
    assertThat(
            jdbc.queryForObject(
                "select accepted_at is null from account_invitation where id = ?",
                Boolean.class,
                UUID.fromString(invitation.get("invitation").get("id").asText())))
        .isTrue();
  }

  private JsonNode invite(String email, String schoolId) throws Exception {
    return create(INVITATIONS, Map.of("email", email, "role", "STUDENT", "schoolId", schoolId));
  }

  private Map<String, String> acceptance(String token) {
    return Map.of(
        "token", token,
        "firstName", "Alicja",
        "lastName", "Nowak",
        "dateOfBirth", "2000-01-01",
        "password", INVITED_PASSWORD);
  }
}
