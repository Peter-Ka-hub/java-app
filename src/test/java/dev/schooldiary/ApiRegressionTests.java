package dev.schooldiary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.schooldiary.identity.internal.persistence.UserRepository;
import dev.schooldiary.organization.internal.persistence.SchoolRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

class ApiRegressionTests extends ApiTestSupport {

  @Autowired UserRepository userRepository;

  @Autowired SchoolRepository schoolRepository;

  @Autowired ObjectMapperProvider documentationMapper;

  @ParameterizedTest
  @MethodSource("invalidPagination")
  void rejectsInvalidPaginationWithBadRequest(String path, String parameter, String value)
      throws Exception {
    mvc.perform(get(path).param(parameter, value))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.path").value(path))
        .andExpect(jsonPath("$.errors").isNotEmpty());
  }

  static Stream<Arguments> invalidPagination() {
    return Stream.of(
            "/api/users",
            "/api/schools",
            "/api/subjects",
            "/api/grades",
            "/api/academic-years",
            "/api/classes",
            "/api/enrollments")
        .flatMap(
            path ->
                Stream.of(
                    Arguments.of(path, "page", "-1"),
                    Arguments.of(path, "size", "0"),
                    Arguments.of(path, "size", "101")));
  }

  @ParameterizedTest
  @MethodSource("invalidUserFields")
  void rejectsInvalidUserData(String field, Object value) throws Exception {
    long usersBefore = userRepository.count();
    Map<String, Object> request =
        userRequest("STUDENT", uniqueEmail(), UUID.randomUUID().toString());
    request.put(field, value);

    mvc.perform(
            post("/api/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").isNotEmpty());

    assertThat(userRepository.count()).isEqualTo(usersBefore);
  }

  static Stream<Arguments> invalidUserFields() {
    return Stream.of(
        Arguments.of("firstName", ""),
        Arguments.of("firstName", " \t\n"),
        Arguments.of("firstName", "a".repeat(51)),
        Arguments.of("lastName", "   "),
        Arguments.of("email", ""),
        Arguments.of("email", "   "),
        Arguments.of("email", "not-an-email"),
        Arguments.of("email", "a".repeat(40) + "@example.com"),
        Arguments.of("dateOfBirth", LocalDate.now().toString()),
        Arguments.of("dateOfBirth", LocalDate.now().plusDays(1).toString()),
        Arguments.of("email", null),
        Arguments.of("dateOfBirth", null));
  }

  @ParameterizedTest
  @MethodSource("invalidSchoolFields")
  void rejectsInvalidSchoolData(String field, Object value) throws Exception {
    long schoolsBefore = schoolRepository.count();
    Map<String, Object> request = schoolRequest();
    request.put(field, value);

    mvc.perform(
            post("/api/schools")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").isNotEmpty());

    assertThat(schoolRepository.count()).isEqualTo(schoolsBefore);
  }

  static Stream<Arguments> invalidSchoolFields() {
    return Stream.of(
        Arguments.of("name", "   "),
        Arguments.of("address", " \t\n"),
        Arguments.of("name", "a".repeat(51)),
        Arguments.of("address", "a".repeat(201)),
        Arguments.of("dateOfConstruction", LocalDate.now().plusDays(1).toString()));
  }

  @Test
  void rejectsWhitespaceSubjectName() throws Exception {
    mvc.perform(
            post("/api/subjects")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("name", "   ", "teacherId", UUID.randomUUID().toString()))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").isNotEmpty());
  }

  @Test
  void rejectsFutureGradeDate() throws Exception {
    mvc.perform(
            post("/api/grades")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "score", "A",
                            "dateOfScore", LocalDate.now().plusDays(1).toString(),
                            "studentId", UUID.randomUUID().toString(),
                            "subjectId", UUID.randomUUID().toString()))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").isNotEmpty());
  }

  @Test
  void normalizesEmailAndRejectsDuplicatesOnCreateAndUpdate() throws Exception {
    String schoolId = createSchool();
    String email = uniqueEmail();
    String firstId = createUser("STUDENT", email.toUpperCase(java.util.Locale.ROOT), schoolId);
    mvc.perform(get("/api/users/{id}", firstId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email));

    mvc.perform(json(post("/api/users"), userRequest("STUDENT", email, schoolId)))
        .andExpect(status().isConflict());

    String secondId = createUser("STUDENT", uniqueEmail(), schoolId);
    mvc.perform(
            json(
                put("/api/users/{id}", secondId),
                withVersion("/api/users/" + secondId, userRequest("STUDENT", email, schoolId))))
        .andExpect(status().isConflict());

    String updatedEmail = uniqueEmail();
    mvc.perform(
            json(
                put("/api/users/{id}", secondId),
                withVersion(
                    "/api/users/" + secondId,
                    userRequest(
                        "STUDENT", updatedEmail.toUpperCase(java.util.Locale.ROOT), schoolId))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(updatedEmail));
  }

  @Test
  void rejectsInvalidDataOnUpdateWithoutChangingStoredUser() throws Exception {
    String schoolId = createSchool();
    String email = uniqueEmail();
    String id = createUser("STUDENT", email, schoolId);
    Map<String, Object> request =
        withVersion("/api/users/" + id, userRequest("STUDENT", email, schoolId));
    request.put("firstName", "   ");

    mvc.perform(
            put("/api/users/{id}", id)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());

    mvc.perform(get("/api/users/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Jan"));
  }

  @Test
  void servesTheSourceContractAndConfiguresSwaggerToUseIt() throws Exception {
    String source;
    try (var input = new ClassPathResource("openapi/openapi.json").getInputStream()) {
      source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
    for (String path : new String[] {"/openapi.json", "/v3/api-docs"}) {
      mvc.perform(get(path).with(anonymous()))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(content().string(source));
    }
    byte[] yaml =
        mvc.perform(get("/v3/api-docs.yaml"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/vnd.oai.openapi"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(documentationMapper.yamlMapper().readTree(yaml))
        .isEqualTo(documentationMapper.jsonMapper().readTree(source));

    mvc.perform(get("/v3/api-docs/swagger-config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("/openapi.json"));
  }

  @Test
  void createAndUpdateEnforceDifferentAccountFields() throws Exception {
    var request = userRequest("STUDENT", uniqueEmail(), createSchool());
    request.remove("password");
    mvc.perform(json(post("/api/users"), request))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("password")));

    request.put("password", PASSWORD);
    request.put("enabled", null);
    request.put("version", null);
    var account = create("/api/users", request);
    assertThat(account.get("enabled").asBoolean()).isTrue();

    String path = "/api/users/" + account.get("id").asText();
    request.put("password", null);
    mvc.perform(json(put(path), request))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("version")));

    request.put("version", account.get("version").asLong());
    request.put("lastName", "Nowak");
    mvc.perform(json(put(path), request))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(jsonPath("$.lastName").value("Nowak"));
    mvc.perform(
            json(
                post("/api/auth/login").with(anonymous()),
                Map.of("email", request.get("email"), "password", PASSWORD)))
        .andExpect(status().isOk());
  }

  @Test
  void responseSchemasAllowTheNullsReturnedForOptionalRelationshipsAndDates() throws Exception {
    var contract = read("/openapi.json");
    String school = createSchool();
    String subject = createSubject(createUser("TEACHER", school), school, createYear(school));
    String enrollment = enroll(createUser("STUDENT", school), subject);

    assertNullableResponse(contract, "/api/auth/me", "/api/auth/me", "schoolId");
    assertNullableResponse(contract, "/api/subjects/{id}", "/api/subjects/" + subject, "classId");
    assertNullableResponse(
        contract, "/api/enrollments/{id}", "/api/enrollments/" + enrollment, "leftOn");
  }

  private void assertNullableResponse(JsonNode contract, String route, String url, String field)
      throws Exception {
    assertThat(read(url).path(field).isNull()).as("%s returns null for %s", url, field).isTrue();
    String reference =
        contract
            .get("paths")
            .get(route)
            .at("/get/responses/200/content/application~1json/schema/$ref")
            .asString();
    assertThat(
            contract
                .at(reference.substring(1))
                .at("/properties/" + field + "/nullable")
                .asBoolean())
        .as("GET %s permits null for %s", route, field)
        .isTrue();
  }

  @Test
  void returnsSafeMessagesForMalformedJsonAndInvalidParameterTypes() throws Exception {
    mvc.perform(
            post("/api/users").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Malformed JSON or invalid field value"));

    mvc.perform(get("/api/users").param("page", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid parameter: page"));
  }

  @Test
  void supportsCreatingReadingUpdatingAndDeletingASchool() throws Exception {
    long schoolsBefore = schoolRepository.count();
    var created =
        mvc.perform(
                post("/api/schools")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(schoolRequest())))
            .andExpect(status().isCreated())
            .andReturn();
    String id =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    mvc.perform(get("/api/schools"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(schoolsBefore + 1));

    Map<String, Object> update = schoolRequest();
    update.put("name", "Updated school");
    update.put("dateOfConstruction", LocalDate.now().toString());
    mvc.perform(json(put("/api/schools/{id}", id), withVersion("/api/schools/" + id, update)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated school"));

    mvc.perform(get("/api/schools/{id}", id)).andExpect(status().isOk());
    mvc.perform(delete("/api/schools/{id}", id).with(csrf())).andExpect(status().isNoContent());
    mvc.perform(get("/api/schools/{id}", id)).andExpect(status().isNotFound());
  }
}
