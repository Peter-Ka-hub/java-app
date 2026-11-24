package dev.schooldiary;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.schooldiary.identity.AccountCredentials;
import dev.schooldiary.identity.AccountPrincipal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.profiles.active=test",
      "spring.datasource.url=jdbc:h2:mem:school-diary-api-tests;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "app.bootstrap.enabled=true",
      "app.bootstrap.admin-email=admin@tests.local",
      "app.bootstrap.admin-password=Test-Admin-123!",
      "app.security.login.max-attempts=1000"
    })
@AutoConfigureMockMvc
@WithAccount(ApiTestSupport.ADMIN_EMAIL)
abstract class ApiTestSupport {
  static final String ADMIN_EMAIL = "admin@tests.local";
  static final String PASSWORD = "Student-Test-12345!";

  @Autowired MockMvc mvc;

  @Autowired ObjectMapper objectMapper;

  @Autowired AccountCredentials credentials;

  RequestPostProcessor actor(String email, String role) {
    var account = credentials.findCredentials(email).orElseThrow();
    if (!account.role().name().equals(role))
      throw new IllegalArgumentException("Test actor role does not match the persisted account");
    var principal = new AccountPrincipal(account);
    principal.eraseCredentials();
    return user(principal);
  }

  JsonNode read(String path) throws Exception {
    return response(
        mvc.perform(get(path))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  JsonNode create(String path, Object body) throws Exception {
    return response(
        mvc.perform(json(post(path), body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body)
      throws Exception {
    return request
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body));
  }

  JsonNode response(String content) {
    return objectMapper.readTree(content);
  }

  String createSchool() throws Exception {
    return create("/api/schools", schoolRequest()).get("id").asText();
  }

  String createUser(String role, String schoolId) throws Exception {
    return createUser(role, uniqueEmail(), schoolId);
  }

  String createUser(String role, String email, String schoolId) throws Exception {
    return create("/api/users", userRequest(role, email, schoolId)).get("id").asText();
  }

  String createYear(String schoolId) throws Exception {
    return create(
            "/api/academic-years",
            Map.of(
                "name",
                "Year " + UUID.randomUUID().toString().substring(0, 8),
                "schoolId",
                schoolId,
                "startDate",
                LocalDate.now().minusMonths(6).toString(),
                "endDate",
                LocalDate.now().plusMonths(6).toString()))
        .get("id")
        .asText();
  }

  String createClass(String schoolId, String yearId) throws Exception {
    return create(
            "/api/classes", Map.of("name", "1A", "schoolId", schoolId, "academicYearId", yearId))
        .get("id")
        .asText();
  }

  String createSubject(String teacherId, String schoolId, String yearId) throws Exception {
    return create(
            "/api/subjects",
            Map.of(
                "name",
                "Mathematics",
                "teacherId",
                teacherId,
                "schoolId",
                schoolId,
                "academicYearId",
                yearId))
        .get("id")
        .asText();
  }

  String enroll(String studentId, String subjectId) throws Exception {
    return create(
            "/api/enrollments",
            Map.of(
                "studentId",
                studentId,
                "subjectId",
                subjectId,
                "joinedOn",
                LocalDate.now().minusDays(2).toString()))
        .get("id")
        .asText();
  }

  Map<String, Object> withVersion(String path, Map<String, Object> request) throws Exception {
    Map<String, Object> result = new LinkedHashMap<>(request);
    result.put("version", read(path).get("version").asLong());
    return result;
  }

  static Map<String, Object> userRequest(String role, String email, String schoolId) {
    Map<String, Object> request =
        new LinkedHashMap<>(
            Map.of(
                "firstName",
                "Jan",
                "lastName",
                "Kowalski",
                "email",
                email,
                "role",
                role,
                "dateOfBirth",
                "2000-01-01",
                "password",
                PASSWORD));
    if (schoolId != null) {
      request.put("schoolId", schoolId);
    }
    return request;
  }

  static Map<String, Object> schoolRequest() {
    return new LinkedHashMap<>(
        Map.of(
            "name",
            "Test school",
            "address",
            "School street 1",
            "dateOfConstruction",
            "2000-09-01",
            "type",
            "PRIMARY"));
  }

  static Map<String, Object> gradeRequest(String studentId, String subjectId, String score) {
    return new LinkedHashMap<>(
        Map.of(
            "studentId",
            studentId,
            "subjectId",
            subjectId,
            "score",
            score,
            "dateOfScore",
            LocalDate.now().toString()));
  }

  static String uniqueEmail() {
    return UUID.randomUUID().toString().substring(0, 12) + "@tests.local";
  }
}
