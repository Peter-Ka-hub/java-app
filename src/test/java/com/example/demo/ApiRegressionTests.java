package com.example.demo;

import com.example.demo.domain.grade.GradeRepository;
import com.example.demo.domain.school.SchoolRepository;
import com.example.demo.domain.subject.SubjectRepository;
import com.example.demo.infrastructure.ports.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:api-regression-tests")
@AutoConfigureMockMvc
class ApiRegressionTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    GradeRepository gradeRepository;

    @Autowired
    SubjectRepository subjectRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SchoolRepository schoolRepository;

    @BeforeEach
    void clearDatabase() {
        gradeRepository.deleteAll();
        subjectRepository.deleteAll();
        userRepository.deleteAll();
        schoolRepository.deleteAll();
    }

    @ParameterizedTest
    @MethodSource("invalidPagination")
    void rejectsInvalidPaginationWithBadRequest(String path, String parameter, String value) throws Exception {
        mvc.perform(get(path).param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value(path))
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    static Stream<Arguments> invalidPagination() {
        return Stream.of("/api/users", "/api/schools", "/api/subjects", "/api/grades")
                .flatMap(path -> Stream.of(
                        Arguments.of(path, "page", "-1"),
                        Arguments.of(path, "size", "0"),
                        Arguments.of(path, "size", "101")));
    }

    @ParameterizedTest
    @MethodSource("invalidUserFields")
    void rejectsInvalidUserData(String field, Object value) throws Exception {
        Map<String, Object> request = userRequest("STUDENT", "student@example.com");
        request.put(field, value);

        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isNotEmpty());

        assertThat(userRepository.count()).isZero();
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
        Map<String, Object> request = schoolRequest();
        request.put(field, value);

        mvc.perform(post("/api/schools").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isNotEmpty());

        assertThat(schoolRepository.count()).isZero();
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
        mvc.perform(post("/api/subjects").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "   ", "teacherId", UUID.randomUUID().toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    void rejectsFutureGradeDate() throws Exception {
        mvc.perform(post("/api/grades").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "score", "A",
                                "dateOfScore", LocalDate.now().plusDays(1).toString(),
                                "studentId", UUID.randomUUID().toString(),
                                "subjectId", UUID.randomUUID().toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    void normalizesEmailAndRejectsDuplicatesOnCreateAndUpdate() throws Exception {
        String firstId = createUser("STUDENT", "Jan.Kowalski@EXAMPLE.COM");
        mvc.perform(get("/api/users/{id}", firstId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jan.kowalski@example.com"));

        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRequest("STUDENT", "JAN.KOWALSKI@example.com"))))
                .andExpect(status().isConflict());

        String secondId = createUser("STUDENT", "second@example.com");
        mvc.perform(put("/api/users/{id}", secondId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRequest("STUDENT", "JAN.KOWALSKI@example.com"))))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/users/{id}", secondId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRequest("STUDENT", "UPDATED@EXAMPLE.COM"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("updated@example.com"));
    }

    @Test
    void rejectsInvalidDataOnUpdateWithoutChangingStoredUser() throws Exception {
        String id = createUser("STUDENT", "student@example.com");
        Map<String, Object> request = userRequest("STUDENT", "student@example.com");
        request.put("firstName", "   ");

        mvc.perform(put("/api/users/{id}", id).contentType(MediaType.APPLICATION_JSON)
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
        mvc.perform(get("/openapi.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(source));

        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/openapi.json"));
    }

    @Test
    void returnsSafeMessagesForMalformedJsonAndInvalidParameterTypes() throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON or invalid field value"));

        mvc.perform(get("/api/users").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid parameter: page"));
    }

    @Test
    void supportsCreatingReadingUpdatingAndDeletingASchool() throws Exception {
        var created = mvc.perform(post("/api/schools").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(schoolRequest())))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(get("/api/schools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        Map<String, Object> update = schoolRequest();
        update.put("name", "Updated school");
        update.put("dateOfConstruction", LocalDate.now().toString());
        mvc.perform(put("/api/schools/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated school"));

        mvc.perform(get("/api/schools/{id}", id)).andExpect(status().isOk());
        mvc.perform(delete("/api/schools/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/schools/{id}", id)).andExpect(status().isNotFound());
    }

    private String createUser(String role, String email) throws Exception {
        var result = mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRequest(role, email))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private static Map<String, Object> userRequest(String role, String email) {
        return new LinkedHashMap<>(Map.of(
                "firstName", "Jan", "lastName", "Kowalski",
                "email", email, "role", role, "dateOfBirth", "2000-01-01"));
    }

    private static Map<String, Object> schoolRequest() {
        return new LinkedHashMap<>(Map.of(
                "name", "Test school", "address", "School street 1",
                "dateOfConstruction", "2000-09-01", "type", "PRIMARY"));
    }
}
