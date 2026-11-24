package dev.schooldiary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

class AttendanceApiTests extends ApiTestSupport {
  @Test
  void capturesTodayAndSupportsSeveralLessonsOfOneSubject() throws Exception {
    Fixture fixture = fixture();
    String endpoint = subjectLessons(fixture.subjectId());
    var first = register(fixture, 1);
    var second = register(fixture, 2);
    assertThat(first.path("lesson").path("lessonDate").asText())
        .isEqualTo(LocalDate.now().toString());
    assertThat(first.path("lesson").path("id").asText())
        .isNotEqualTo(second.path("lesson").path("id").asText());
    assertThat(first.path("entries").size()).isEqualTo(1);
    assertThat(first.path("entries").get(0).path("status").isNull()).isTrue();
    mvc.perform(get(endpoint).with(fixture.teacher()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
    mvc.perform(json(post(endpoint).with(fixture.teacher()), Map.of("lessonNumber", 1)))
        .andExpect(status().isConflict());
    mvc.perform(
            json(
                post(endpoint).with(fixture.teacher()),
                Map.of("lessonNumber", 3, "lessonDate", LocalDate.now().minusDays(1).toString())))
        .andExpect(status().isBadRequest());
  }

  @Test
  void protectsLessonListDetailsCreationAndWritesFromUnassignedAndForeignTeachers()
      throws Exception {
    Fixture fixture = fixture();
    var lesson = register(fixture, 1);
    String sameSchoolTeacher = uniqueEmail();
    createUser("TEACHER", sameSchoolTeacher, fixture.schoolId());
    String foreignTeacher = uniqueEmail();
    createUser("TEACHER", foreignTeacher, createSchool());
    String foreignAdmin = uniqueEmail();
    createUser("ADMIN", foreignAdmin, createSchool());
    var denied =
        List.of(
            actor(sameSchoolTeacher, "TEACHER"),
            actor(foreignTeacher, "TEACHER"),
            actor(foreignAdmin, "ADMIN"),
            actor(fixture.studentEmail(), "STUDENT"));
    for (var caller : denied) {
      mvc.perform(get(subjectLessons(fixture.subjectId())).with(caller))
          .andExpect(status().isForbidden());
      mvc.perform(get(lessonPath(lesson)).with(caller)).andExpect(status().isForbidden());
      mvc.perform(
              json(
                  post(subjectLessons(fixture.subjectId())).with(caller),
                  Map.of("lessonNumber", 2)))
          .andExpect(status().isForbidden());
      mvc.perform(
              json(
                  put(lessonPath(lesson)).with(caller),
                  saveBody(lesson, "DRAFT", List.of(mark(fixture.studentId(), "LATE")))))
          .andExpect(status().isForbidden());
    }
    assertThat(read(lessonPath(lesson))).isEqualTo(lesson);
  }

  @Test
  void rejectsForeignDuplicateAndMissingRowsWithoutApplyingAnyMarks() throws Exception {
    Fixture fixture = fixture();
    String second = createUser("STUDENT", fixture.schoolId());
    enroll(second, fixture.subjectId());
    String notOnRoster = createUser("STUDENT", fixture.schoolId());
    var lesson = register(fixture, 1);
    var validFirst = mark(fixture.studentId(), "LATE");
    List<List<Map<String, Object>>> invalidRows =
        List.of(
            List.of(validFirst, mark(notOnRoster, "ABSENT")),
            List.of(validFirst, mark(fixture.studentId(), "ABSENT")),
            List.of(validFirst));
    for (var rows : invalidRows) {
      mvc.perform(
              json(
                  put(lessonPath(lesson)).with(fixture.teacher()), saveBody(lesson, "DRAFT", rows)))
          .andExpect(status().isBadRequest());
      assertThat(read(lessonPath(lesson))).isEqualTo(lesson);
    }
    var partial = save(fixture, lesson, "DRAFT", List.of(validFirst, mark(second, null)));
    assertThat(partial.path("lesson").path("version").asLong())
        .isEqualTo(lesson.path("lesson").path("version").asLong() + 1);
    mvc.perform(
            json(
                put(lessonPath(partial)).with(fixture.teacher()),
                saveBody(partial, "FINALIZED", List.of(validFirst, mark(second, null)))))
        .andExpect(status().isBadRequest());
    assertThat(read(lessonPath(partial))).isEqualTo(partial);
  }

  @Test
  void preservesRosterWhenStudentsWithdrawAndJoinAfterRegistration() throws Exception {
    Fixture fixture = fixture();
    var first = register(fixture, 1);
    String enrollmentPath = "/api/enrollments/" + fixture.enrollmentId();
    mvc.perform(
            json(
                put(enrollmentPath),
                withVersion(
                    enrollmentPath,
                    Map.of("status", "WITHDRAWN", "leftOn", LocalDate.now().toString()))))
        .andExpect(status().isOk());
    String laterStudent = createUser("STUDENT", fixture.schoolId());
    enroll(laterStudent, fixture.subjectId());

    var finalized = save(fixture, first, "FINALIZED", List.of(mark(fixture.studentId(), "ABSENT")));
    assertThat(finalized.path("entries").size()).isEqualTo(1);
    assertThat(finalized.path("entries").get(0).path("studentId").asText())
        .isEqualTo(fixture.studentId());
    var second = register(fixture, 2);
    assertThat(second.path("entries").size()).isEqualTo(1);
    assertThat(second.path("entries").get(0).path("studentId").asText()).isEqualTo(laterStudent);
  }

  @Test
  void finalizedCorrectionsRecordActorTimeAndPriorStatusAndRejectStaleWrites() throws Exception {
    Fixture fixture = fixture();
    var draft = register(fixture, 1);
    var finalized =
        save(fixture, draft, "FINALIZED", List.of(mark(fixture.studentId(), "PRESENT")));
    var corrected =
        save(fixture, finalized, "FINALIZED", List.of(mark(fixture.studentId(), "LATE")));
    assertThat(corrected.path("lesson").path("version").asLong())
        .isEqualTo(finalized.path("lesson").path("version").asLong() + 1);
    var revisions = corrected.path("entries").get(0).path("corrections");
    assertThat(revisions.size()).isEqualTo(1);
    assertThat(revisions.get(0).path("previousStatus").asText()).isEqualTo("PRESENT");
    assertThat(revisions.get(0).path("newStatus").asText()).isEqualTo("LATE");
    assertThat(revisions.get(0).path("changedById").asText()).isEqualTo(fixture.teacherId());
    assertThat(java.time.OffsetDateTime.parse(revisions.get(0).path("changedAt").asText()))
        .isBeforeOrEqualTo(java.time.OffsetDateTime.now());

    mvc.perform(
            json(
                put(lessonPath(finalized)).with(fixture.teacher()),
                saveBody(finalized, "FINALIZED", List.of(mark(fixture.studentId(), "ABSENT")))))
        .andExpect(status().isConflict());
    mvc.perform(
            json(
                put(lessonPath(corrected)).with(fixture.teacher()),
                saveBody(corrected, "DRAFT", List.of(mark(fixture.studentId(), null)))))
        .andExpect(status().isConflict());
    assertThat(read(lessonPath(corrected))).isEqualTo(corrected);
  }

  @Test
  void teacherCanEnrollNewStudentByExactEmailOnlyWithinAssignedSubjectAndSchool() throws Exception {
    Fixture fixture = fixture();
    String newEmail = uniqueEmail();
    String newStudent = createUser("STUDENT", newEmail, fixture.schoolId());
    String otherEmail = uniqueEmail();
    createUser("STUDENT", otherEmail, createSchool());
    String otherTeacher = uniqueEmail();
    createUser("TEACHER", otherTeacher, fixture.schoolId());
    String path = "/api/subjects/" + fixture.subjectId() + "/enrollments/by-email";
    var payload =
        Map.of("email", newEmail.toUpperCase(Locale.ROOT), "joinedOn", LocalDate.now().toString());
    mvc.perform(json(post(path).with(actor(otherTeacher, "TEACHER")), payload))
        .andExpect(status().isForbidden());
    mvc.perform(
            json(
                post(path).with(fixture.teacher()),
                Map.of("email", otherEmail, "joinedOn", LocalDate.now().toString())))
        .andExpect(status().isNotFound());
    mvc.perform(json(post(path).with(fixture.teacher()), payload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.studentId").value(newStudent));
    mvc.perform(json(post(path).with(fixture.teacher()), payload)).andExpect(status().isConflict());
    assertThat(register(fixture, 1).path("entries").size()).isEqualTo(2);
  }

  private Fixture fixture() throws Exception {
    String school = createSchool();
    String year = createYear(school);
    String teacherEmail = uniqueEmail();
    String teacher = createUser("TEACHER", teacherEmail, school);
    String studentEmail = uniqueEmail();
    String student = createUser("STUDENT", studentEmail, school);
    String subject = createSubject(teacher, school, year);
    return new Fixture(
        school,
        teacher,
        student,
        studentEmail,
        subject,
        enroll(student, subject),
        actor(teacherEmail, "TEACHER"));
  }

  private JsonNode register(Fixture fixture, int number) throws Exception {
    return response(
        mvc.perform(
                json(
                    post(subjectLessons(fixture.subjectId())).with(fixture.teacher()),
                    Map.of("lessonNumber", number)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode save(
      Fixture fixture, JsonNode lesson, String state, List<Map<String, Object>> rows)
      throws Exception {
    return response(
        mvc.perform(
                json(
                    put(lessonPath(lesson)).with(fixture.teacher()), saveBody(lesson, state, rows)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private static String subjectLessons(String subjectId) {
    return "/api/subjects/" + subjectId + "/attendance-lessons";
  }

  private static String lessonPath(JsonNode lesson) {
    return "/api/attendance/lessons/" + lesson.path("lesson").path("id").asText();
  }

  private static Map<String, Object> mark(String student, String status) {
    var row = new LinkedHashMap<String, Object>();
    row.put("studentId", student);
    row.put("status", status);
    return row;
  }

  private static Map<String, Object> saveBody(
      JsonNode lesson, String state, List<Map<String, Object>> rows) {
    return Map.of(
        "version", lesson.path("lesson").path("version").asLong(), "state", state, "entries", rows);
  }

  private record Fixture(
      String schoolId,
      String teacherId,
      String studentId,
      String studentEmail,
      String subjectId,
      String enrollmentId,
      RequestPostProcessor teacher) {}
}
