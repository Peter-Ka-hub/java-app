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

    private Map<String, Object> schoolRequest() {
        return null;
    }

    static Stream<Arguments> invalidSchoolFields() {