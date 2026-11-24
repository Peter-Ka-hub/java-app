package dev.schooldiary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class AttendanceReportTests extends ApiTestSupport {
  @Test
  void schoolReportCountsFinalizedRowsAcrossPagesUsingTheClassAtRegistration() throws Exception {
    var fixture = subjectIn(createSchool());
    var present = enrollStudent(fixture);
    var absent = enrollStudent(fixture);
    var late = enrollStudent(fixture);
    var finalized = Map.of(present.id(), "PRESENT", absent.id(), "ABSENT", late.id(), "LATE");
    var draft = Map.of(present.id(), "PRESENT", absent.id(), "PRESENT", late.id(), "PRESENT");
    String lessonId = saveLesson(fixture, 1, "FINALIZED", finalized);
    saveLesson(fixture, 2, "DRAFT", draft);

    String newClass =
        create(
                "/api/classes",
                Map.of(
                    "name",
                    "1B",
                    "schoolId",
                    fixture.schoolId(),
                    "academicYearId",
                    fixture.yearId()))
            .get("id")
            .asText();
    String subjectPath = "/api/subjects/" + fixture.subjectId();
    mvc.perform(
            json(
                put(subjectPath),
                withVersion(
                    subjectPath,
                    Map.of(
                        "name", "Mathematics",
                        "teacherId", fixture.teacherId(),
                        "schoolId", fixture.schoolId(),
                        "academicYearId", fixture.yearId(),
                        "classId", newClass))))
        .andExpect(status().isOk());

    String adminEmail = uniqueEmail();
    createUser("ADMIN", adminEmail, fixture.schoolId());
    var first =
        mvc.perform(
                get("/api/attendance/report")
                    .with(actor(adminEmail, "ADMIN"))
                    .param("from", LocalDate.now().toString())
                    .param("to", LocalDate.now().toString())
                    .param("classId", fixture.classId())
                    .param("subjectId", fixture.subjectId())
                    .param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.present").value(1))
            .andExpect(jsonPath("$.summary.absent").value(1))
            .andExpect(jsonPath("$.summary.late").value(1))
            .andExpect(jsonPath("$.summary.total").value(3))
            .andExpect(jsonPath("$.summary.attendancePercentage").value(66.67))
            .andExpect(jsonPath("$.entries.totalElements").value(3))
            .andExpect(jsonPath("$.entries.totalPages").value(3))
            .andExpect(jsonPath("$.entries.items.length()").value(1))
            .andExpect(jsonPath("$.entries.items[0].lessonId").value(lessonId))
            .andExpect(jsonPath("$.entries.items[0].classId").value(fixture.classId()))
            .andExpect(jsonPath("$.entries.items[0].className").value("1A"))
            .andReturn();
    var second =
        mvc.perform(
                get("/api/attendance/report")
                    .with(actor(adminEmail, "ADMIN"))
                    .param("from", LocalDate.now().toString())
                    .param("to", LocalDate.now().toString())
                    .param("classId", fixture.classId())
                    .param("subjectId", fixture.subjectId())
                    .param("page", "1")
                    .param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.total").value(3))
            .andExpect(jsonPath("$.entries.items.length()").value(1))
            .andReturn();
    assertThat(response(first.getResponse().getContentAsString()).at("/entries/items/0/studentId"))
        .isNotEqualTo(
            response(second.getResponse().getContentAsString()).at("/entries/items/0/studentId"));

    assertEmpty(
        mvc.perform(
            get("/api/attendance/report")
                .with(actor(adminEmail, "ADMIN"))
                .param("from", LocalDate.now().toString())
                .param("to", LocalDate.now().toString())
                .param("classId", newClass)
                .param("subjectId", fixture.subjectId())));
  }

  @Test
  void studentSeesOnlyOwnFinalizedRowsIncludingAfterWithdrawal() throws Exception {
    var fixture = subjectIn(createSchool());
    var student = enrollStudent(fixture);
    var peer = enrollStudent(fixture);
    String lessonId =
        saveLesson(fixture, 1, "FINALIZED", Map.of(student.id(), "LATE", peer.id(), "ABSENT"));
    saveLesson(fixture, 2, "DRAFT", Map.of(student.id(), "PRESENT", peer.id(), "PRESENT"));

    String enrollmentPath = "/api/enrollments/" + student.enrollmentId();
    mvc.perform(
            json(
                put(enrollmentPath),
                withVersion(
                    enrollmentPath,
                    Map.of("status", "WITHDRAWN", "leftOn", LocalDate.now().toString()))))
        .andExpect(status().isOk());

    mvc.perform(
            get("/api/attendance/me")
                .with(actor(student.email(), "STUDENT"))
                .param("from", LocalDate.now().toString())
                .param("to", LocalDate.now().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.present").value(0))
        .andExpect(jsonPath("$.summary.absent").value(0))
        .andExpect(jsonPath("$.summary.late").value(1))
        .andExpect(jsonPath("$.summary.total").value(1))
        .andExpect(jsonPath("$.summary.attendancePercentage").value(100.0))
        .andExpect(jsonPath("$.entries.items.length()").value(1))
        .andExpect(jsonPath("$.entries.items[0].lessonId").value(lessonId))
        .andExpect(jsonPath("$.entries.items[0].studentId").value(student.id()))
        .andExpect(jsonPath("$.entries.items[0].status").value("LATE"));

    String yesterday = LocalDate.now().minusDays(1).toString();
    assertEmpty(
        mvc.perform(
            get("/api/attendance/me")
                .with(actor(student.email(), "STUDENT"))
                .param("from", yesterday)
                .param("to", yesterday)));
    mvc.perform(
            get("/api/attendance/report")
                .with(actor(student.email(), "STUDENT"))
                .param("from", yesterday)
                .param("to", yesterday))
        .andExpect(status().isForbidden());
  }

  @Test
  void schoolReportDeniesForeignFiltersAndDefaultsToTheAdministratorsSchool() throws Exception {
    String ownSchool = createSchool();
    String adminEmail = uniqueEmail();
    createUser("ADMIN", adminEmail, ownSchool);
    var foreign = subjectIn(createSchool());
    var foreignStudent = enrollStudent(foreign);
    saveLesson(foreign, 1, "FINALIZED", Map.of(foreignStudent.id(), "ABSENT"));

    var schoolAdmin = actor(adminEmail, "ADMIN");
    String today = LocalDate.now().toString();
    assertEmpty(
        mvc.perform(
            get("/api/attendance/report")
                .with(schoolAdmin)
                .param("from", today)
                .param("to", today)));
    for (var filter :
        Map.of(
                "schoolId",
                foreign.schoolId(),
                "classId",
                foreign.classId(),
                "subjectId",
                foreign.subjectId())
            .entrySet()) {
      mvc.perform(
              get("/api/attendance/report")
                  .with(schoolAdmin)
                  .param("from", today)
                  .param("to", today)
                  .param(filter.getKey(), filter.getValue()))
          .andExpect(status().isForbidden());
    }
    mvc.perform(
            get("/api/attendance/report")
                .param("from", today)
                .param("to", today)
                .param("schoolId", foreign.schoolId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.absent").value(1))
        .andExpect(jsonPath("$.summary.attendancePercentage").value(0.0))
        .andExpect(jsonPath("$.entries.items[0].schoolId").value(foreign.schoolId()));

    mvc.perform(
            get("/api/attendance/report")
                .param("from", LocalDate.now().minusDays(366).toString())
                .param("to", today))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/attendance/report")
                .param("from", today)
                .param("to", LocalDate.now().minusDays(1).toString()))
        .andExpect(status().isBadRequest());
  }

  private static void assertEmpty(ResultActions report) throws Exception {
    report
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.present").value(0))
        .andExpect(jsonPath("$.summary.absent").value(0))
        .andExpect(jsonPath("$.summary.late").value(0))
        .andExpect(jsonPath("$.summary.total").value(0))
        .andExpect(jsonPath("$.summary.attendancePercentage").value(nullValue()))
        .andExpect(jsonPath("$.entries.items").isEmpty())
        .andExpect(jsonPath("$.entries.totalElements").value(0));
  }

  private SubjectFixture subjectIn(String schoolId) throws Exception {
    String year = createYear(schoolId);
    String schoolClass = createClass(schoolId, year);
    String teacher = createUser("TEACHER", schoolId);
    String subject =
        create(
                "/api/subjects",
                Map.of(
                    "name", "Mathematics",
                    "teacherId", teacher,
                    "schoolId", schoolId,
                    "academicYearId", year,
                    "classId", schoolClass))
            .get("id")
            .asText();
    return new SubjectFixture(schoolId, year, schoolClass, teacher, subject);
  }

  private Enrolled enrollStudent(SubjectFixture subject) throws Exception {
    String email = uniqueEmail();
    String id = createUser("STUDENT", email, subject.schoolId());
    return new Enrolled(id, email, enroll(id, subject.subjectId()));
  }

  private String saveLesson(
      SubjectFixture fixture, int number, String state, Map<String, String> marks)
      throws Exception {
    var lesson =
        create(
                "/api/subjects/" + fixture.subjectId() + "/attendance-lessons",
                Map.of("lessonNumber", number))
            .get("lesson");
    String id = lesson.get("id").asText();
    var entries =
        marks.entrySet().stream()
            .map(mark -> Map.of("studentId", mark.getKey(), "status", mark.getValue()))
            .toList();
    mvc.perform(
            json(
                put("/api/attendance/lessons/{id}", id),
                Map.of(
                    "version", lesson.get("version").asLong(), "state", state, "entries", entries)))
        .andExpect(status().isOk());
    return id;
  }

  private record SubjectFixture(
      String schoolId, String yearId, String classId, String teacherId, String subjectId) {}

  private record Enrolled(String id, String email, String enrollmentId) {}
}
