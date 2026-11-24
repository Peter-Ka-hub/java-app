package dev.schooldiary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class SchoolDiaryWorkflowTests extends ApiTestSupport {
  @Test
  void supportsUpdatingAndDeletingUnusedAcademicYearsAndClasses() throws Exception {
    String school = createSchool();
    String year = createYear(school);
    String schoolClass = createClass(school, year);

    var yearUpdate =
        new LinkedHashMap<String, Object>(
            Map.of(
                "name",
                "Updated year",
                "schoolId",
                school,
                "startDate",
                LocalDate.now().minusMonths(6).toString(),
                "endDate",
                LocalDate.now().plusMonths(6).toString()));
    mvc.perform(
            json(
                put("/api/academic-years/{id}", year),
                withVersion("/api/academic-years/" + year, yearUpdate)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated year"));
    var classUpdate =
        new LinkedHashMap<String, Object>(
            Map.of("name", "1B", "schoolId", school, "academicYearId", year));
    mvc.perform(
            json(
                put("/api/classes/{id}", schoolClass),
                withVersion("/api/classes/" + schoolClass, classUpdate)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("1B"));
    mvc.perform(delete("/api/classes/{id}", schoolClass).with(csrf()))
        .andExpect(status().isNoContent());
    mvc.perform(delete("/api/academic-years/{id}", year).with(csrf()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/classes/{id}", schoolClass)).andExpect(status().isNotFound());
    mvc.perform(get("/api/academic-years/{id}", year)).andExpect(status().isNotFound());
  }

  @Test
  void supportsClassEnrollmentGradingCorrectionWithdrawalAndAuditHistory() throws Exception {
    Fixture f = fixture();
    String classId = createClass(f.school(), f.year());
    var subjectUpdate =
        new LinkedHashMap<String, Object>(
            Map.of(
                "name",
                "Mathematics",
                "teacherId",
                f.teacher(),
                "schoolId",
                f.school(),
                "academicYearId",
                f.year(),
                "classId",
                classId));
    mvc.perform(
            json(
                put("/api/subjects/{id}", f.subject()),
                withVersion("/api/subjects/" + f.subject(), subjectUpdate)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.classId").value(classId));

    JsonNode created =
        response(
            mvc.perform(
                    json(
                        post("/api/grades").with(actor(f.teacherEmail(), "TEACHER")),
                        gradeRequest(f.student(), f.subject(), "B")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gradedById").value(f.teacher()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString());
    String gradeId = created.get("id").asText();

    mvc.perform(get("/api/grades/{id}", gradeId).with(actor(f.studentEmail(), "STUDENT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.score").value("B"));

    mvc.perform(
            json(
                put("/api/grades/{id}", gradeId).with(actor(f.teacherEmail(), "TEACHER")),
                withVersion("/api/grades/" + gradeId, gradeRequest(f.student(), f.subject(), "A"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.score").value("A"));

    JsonNode history = read("/api/grades/" + gradeId + "/history");
    assertThat(history.size()).isEqualTo(2);
    assertThat(history.toString()).contains("\"score\":\"B\"", "\"score\":\"A\"");
    assertThat(history.toString()).contains(f.teacher());

    var withdrawal =
        withVersion(
            "/api/enrollments/" + f.enrollment(),
            new LinkedHashMap<>(
                Map.of("status", "WITHDRAWN", "leftOn", LocalDate.now().toString())));
    mvc.perform(json(put("/api/enrollments/{id}", f.enrollment()), withdrawal))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    mvc.perform(get("/api/grades/{id}", gradeId).with(actor(f.studentEmail(), "STUDENT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.score").value("A"));

    mvc.perform(
            delete("/api/grades/{id}", gradeId)
                .with(csrf())
                .with(actor(f.teacherEmail(), "TEACHER")))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/grades/{id}", gradeId)).andExpect(status().isNotFound());
    mvc.perform(get("/api/grades/{id}/history", gradeId).with(actor(f.studentEmail(), "STUDENT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3));
  }

  @Test
  void studentOnlySeesTheirOwnProfileGradesAndSubjects() throws Exception {
    Fixture f = fixture();
    String otherStudent = createUser("STUDENT", f.school());
    enroll(otherStudent, f.subject());
    String gradeId =
        create("/api/grades", gradeRequest(f.student(), f.subject(), "A")).get("id").asText();
    mvc.perform(get("/api/users").with(actor(f.studentEmail(), "STUDENT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].id").value(f.student()));
    mvc.perform(get("/api/subjects").with(actor(f.studentEmail(), "STUDENT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].id").value(f.subject()));
    mvc.perform(get("/api/subjects/{id}", f.subject()).with(actor(f.studentEmail(), "STUDENT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentIds.length()").value(1))
        .andExpect(jsonPath("$.studentIds[0]").value(f.student()));
    mvc.perform(get("/api/grades").with(actor(f.studentEmail(), "STUDENT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].id").value(gradeId));
    mvc.perform(get("/api/users/{id}", otherStudent).with(actor(f.studentEmail(), "STUDENT")))
        .andExpect(status().isForbidden());
    mvc.perform(
            json(
                post("/api/grades").with(actor(f.studentEmail(), "STUDENT")),
                gradeRequest(f.student(), f.subject(), "A")))
        .andExpect(status().isForbidden());
  }

  @Test
  void teacherOnlySeesTheirSubjectsAndEnrolledStudents() throws Exception {
    Fixture f = fixture();
    String otherTeacher = createUser("TEACHER", f.school());
    String otherSubject = createSubject(otherTeacher, f.school(), f.year());
    String unenrolledStudent = createUser("STUDENT", f.school());
    mvc.perform(get("/api/subjects").with(actor(f.teacherEmail(), "TEACHER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].id").value(f.subject()));
    mvc.perform(get("/api/users").with(actor(f.teacherEmail(), "TEACHER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].id").value(f.student()));
    mvc.perform(get("/api/users/{id}", f.student()).with(actor(f.teacherEmail(), "TEACHER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(f.student()));
    mvc.perform(get("/api/subjects/{id}", otherSubject).with(actor(f.teacherEmail(), "TEACHER")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/users/{id}", unenrolledStudent).with(actor(f.teacherEmail(), "TEACHER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void teacherCannotChangeAGradeFromAnotherTeachersSubject() throws Exception {
    Fixture f = fixture();
    String otherTeacher = createUser("TEACHER", f.school());
    String otherSubject = createSubject(otherTeacher, f.school(), f.year());
    enroll(f.student(), otherSubject);
    String gradeId =
        create("/api/grades", gradeRequest(f.student(), otherSubject, "C")).get("id").asText();
    mvc.perform(get("/api/grades/{id}", gradeId).with(actor(f.teacherEmail(), "TEACHER")))
        .andExpect(status().isForbidden());
    mvc.perform(
            json(
                put("/api/grades/{id}", gradeId).with(actor(f.teacherEmail(), "TEACHER")),
                withVersion(
                    "/api/grades/" + gradeId, gradeRequest(f.student(), otherSubject, "A"))))
        .andExpect(status().isForbidden());
    mvc.perform(
            delete("/api/grades/{id}", gradeId)
                .with(csrf())
                .with(actor(f.teacherEmail(), "TEACHER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void schoolAdministratorCannotReadOrWriteAnotherSchoolsRecords() throws Exception {
    Fixture f = fixture();
    String adminEmail = uniqueEmail();
    createUser("ADMIN", adminEmail, f.school());
    String otherSchool = createSchool();
    String otherStudent = createUser("STUDENT", otherSchool);
    mvc.perform(get("/api/users").with(actor(adminEmail, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3));
    mvc.perform(get("/api/schools").with(actor(adminEmail, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get("/api/users/{id}", otherStudent).with(actor(adminEmail, "ADMIN")))
        .andExpect(status().isForbidden());
    mvc.perform(
            json(
                post("/api/users").with(actor(adminEmail, "ADMIN")),
                userRequest("STUDENT", uniqueEmail(), otherSchool)))
        .andExpect(status().isForbidden());
    mvc.perform(
            json(
                post("/api/users").with(actor(adminEmail, "ADMIN")),
                userRequest("ADMIN", uniqueEmail(), null)))
        .andExpect(status().isForbidden());
  }

  @Test
  void changingAssignedTeacherRoleOrSchoolIsRejected() throws Exception {
    Fixture f = fixture();
    var roleChange =
        withVersion(
            "/api/users/" + f.teacher(), userRequest("STUDENT", f.teacherEmail(), f.school()));
    mvc.perform(json(put("/api/users/{id}", f.teacher()), roleChange))
        .andExpect(status().isConflict());
    var schoolChange =
        withVersion(
            "/api/users/" + f.teacher(), userRequest("TEACHER", f.teacherEmail(), createSchool()));
    mvc.perform(json(put("/api/users/{id}", f.teacher()), schoolChange))
        .andExpect(status().isConflict());
    mvc.perform(get("/api/users/{id}", f.teacher()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("TEACHER"))
        .andExpect(jsonPath("$.schoolId").value(f.school()));
  }

  @Test
  void staleVersionDoesNotOverwriteANewerUpdate() throws Exception {
    String school = createSchool();
    var first = withVersion("/api/schools/" + school, schoolRequest());
    first.put("name", "First update");
    mvc.perform(json(put("/api/schools/{id}", school), first)).andExpect(status().isOk());
    first.put("name", "Stale update");
    mvc.perform(json(put("/api/schools/{id}", school), first)).andExpect(status().isConflict());
    mvc.perform(get("/api/schools/{id}", school))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("First update"));
  }

  @Test
  void updatesRequireTheCurrentVersion() throws Exception {
    String school = createSchool();
    mvc.perform(json(put("/api/schools/{id}", school), schoolRequest()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void studentsAndTeachersRequireASchool() throws Exception {
    for (String role : new String[] {"STUDENT", "TEACHER"}) {
      mvc.perform(json(post("/api/users"), userRequest(role, uniqueEmail(), null)))
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  void academicYearMustHaveAnOrderedDateRange() throws Exception {
    String school = createSchool();
    mvc.perform(
            json(
                post("/api/academic-years"),
                Map.of(
                    "name",
                    "Invalid range",
                    "schoolId",
                    school,
                    "startDate",
                    LocalDate.now().toString(),
                    "endDate",
                    LocalDate.now().minusDays(1).toString())))
        .andExpect(status().isBadRequest());
  }

  @Test
  void academicYearDatesCannotChangeOnceSubjectsHaveBeenAssigned() throws Exception {
    Fixture f = fixture();
    var update =
        new LinkedHashMap<String, Object>(
            Map.of(
                "name",
                "Changed year",
                "schoolId",
                f.school(),
                "startDate",
                LocalDate.now().minusMonths(7).toString(),
                "endDate",
                LocalDate.now().plusMonths(6).toString()));
    mvc.perform(
            json(
                put("/api/academic-years/{id}", f.year()),
                withVersion("/api/academic-years/" + f.year(), update)))
        .andExpect(status().isConflict());
    update.put("startDate", LocalDate.now().minusMonths(6).toString());
    mvc.perform(
            json(
                put("/api/academic-years/{id}", f.year()),
                withVersion("/api/academic-years/" + f.year(), update)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Changed year"));
  }

  @Test
  void withdrawalCannotInvalidateTheDateOfADeletedGradeInAuditHistory() throws Exception {
    Fixture f = fixture();
    String gradeId =
        create("/api/grades", gradeRequest(f.student(), f.subject(), "A")).get("id").asText();
    mvc.perform(delete("/api/grades/{id}", gradeId).with(csrf())).andExpect(status().isNoContent());
    var withdrawal =
        new LinkedHashMap<String, Object>(
            Map.of("status", "WITHDRAWN", "leftOn", LocalDate.now().minusDays(1).toString()));
    mvc.perform(
            json(
                put("/api/enrollments/{id}", f.enrollment()),
                withVersion("/api/enrollments/" + f.enrollment(), withdrawal)))
        .andExpect(status().isConflict());
    mvc.perform(get("/api/enrollments/{id}", f.enrollment()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void academicYearAndClassMustBelongToTheSubjectsSchool() throws Exception {
    Fixture f = fixture();
    String otherSchool = createSchool();
    String otherYear = createYear(otherSchool);
    mvc.perform(
            json(
                post("/api/subjects"),
                Map.of(
                    "name",
                    "Invalid school-year",
                    "teacherId",
                    f.teacher(),
                    "schoolId",
                    f.school(),
                    "academicYearId",
                    otherYear)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            json(
                post("/api/classes"),
                Map.of(
                    "name", "Invalid class", "schoolId", f.school(), "academicYearId", otherYear)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void cannotEnrollATeacherOrAStudentFromAnotherSchool() throws Exception {
    Fixture f = fixture();
    mvc.perform(
            json(
                post("/api/enrollments"),
                Map.of(
                    "studentId",
                    f.teacher(),
                    "subjectId",
                    f.subject(),
                    "joinedOn",
                    LocalDate.now().toString())))
        .andExpect(status().isBadRequest());
    String outsider = createUser("STUDENT", createSchool());
    mvc.perform(
            json(
                post("/api/enrollments"),
                Map.of(
                    "studentId",
                    outsider,
                    "subjectId",
                    f.subject(),
                    "joinedOn",
                    LocalDate.now().toString())))
        .andExpect(status().isBadRequest());
  }

  @Test
  void subjectsRequireATeacherFromTheSameSchool() throws Exception {
    Fixture f = fixture();
    mvc.perform(
            json(
                post("/api/subjects"),
                Map.of(
                    "name",
                    "Invalid teacher",
                    "teacherId",
                    f.student(),
                    "schoolId",
                    f.school(),
                    "academicYearId",
                    f.year())))
        .andExpect(status().isBadRequest());
    String foreignTeacher = createUser("TEACHER", createSchool());
    mvc.perform(
            json(
                post("/api/subjects"),
                Map.of(
                    "name",
                    "Foreign teacher",
                    "teacherId",
                    foreignTeacher,
                    "schoolId",
                    f.school(),
                    "academicYearId",
                    f.year())))
        .andExpect(status().isBadRequest());
  }

  @Test
  void duplicateEnrollmentIsAConflict() throws Exception {
    Fixture f = fixture();
    mvc.perform(
            json(
                post("/api/enrollments"),
                Map.of(
                    "studentId",
                    f.student(),
                    "subjectId",
                    f.subject(),
                    "joinedOn",
                    LocalDate.now().toString())))
        .andExpect(status().isConflict());
  }

  @Test
  void gradeRequiresAnActiveEnrollmentAndADateWithinTheAcademicYear() throws Exception {
    Fixture f = fixture();
    String stranger = createUser("STUDENT", f.school());
    mvc.perform(json(post("/api/grades"), gradeRequest(stranger, f.subject(), "A")))
        .andExpect(status().isBadRequest());
    var outsideYear = gradeRequest(f.student(), f.subject(), "A");
    outsideYear.put("dateOfScore", LocalDate.now().minusYears(2).toString());
    mvc.perform(json(post("/api/grades"), outsideYear)).andExpect(status().isBadRequest());
    var beforeEnrollment = gradeRequest(f.student(), f.subject(), "A");
    beforeEnrollment.put("dateOfScore", LocalDate.now().minusDays(3).toString());
    mvc.perform(json(post("/api/grades"), beforeEnrollment)).andExpect(status().isBadRequest());

    mvc.perform(
            json(
                put("/api/enrollments/{id}", f.enrollment()),
                withVersion(
                    "/api/enrollments/" + f.enrollment(),
                    new LinkedHashMap<>(
                        Map.of("status", "WITHDRAWN", "leftOn", LocalDate.now().toString())))))
        .andExpect(status().isOk());
    mvc.perform(json(post("/api/grades"), gradeRequest(f.student(), f.subject(), "A")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void recordsWithHistoryCannotBeDeletedThroughTheirParents() throws Exception {
    Fixture f = fixture();
    create("/api/grades", gradeRequest(f.student(), f.subject(), "B"));
    mvc.perform(delete("/api/subjects/{id}", f.subject()).with(csrf()))
        .andExpect(status().isConflict());
    mvc.perform(delete("/api/users/{id}", f.student()).with(csrf()))
        .andExpect(status().isConflict());
    mvc.perform(delete("/api/schools/{id}", f.school()).with(csrf()))
        .andExpect(status().isConflict());
  }

  @Test
  void missingRecordsReturnNotFoundToAnAuthorizedAdministrator() throws Exception {
    for (String collection :
        new String[] {
          "schools", "users", "subjects", "grades", "academic-years", "classes", "enrollments"
        }) {
      mvc.perform(get("/api/" + collection + "/" + UUID.randomUUID()))
          .andExpect(status().isNotFound());
    }
  }

  private Fixture fixture() throws Exception {
    String school = createSchool();
    String year = createYear(school);
    String teacherEmail = uniqueEmail();
    String studentEmail = uniqueEmail();
    String teacher = createUser("TEACHER", teacherEmail, school);
    String student = createUser("STUDENT", studentEmail, school);
    String subject = createSubject(teacher, school, year);
    String enrollment = enroll(student, subject);
    return new Fixture(
        school, year, teacher, teacherEmail, student, studentEmail, subject, enrollment);
  }

  private record Fixture(
      String school,
      String year,
      String teacher,
      String teacherEmail,
      String student,
      String studentEmail,
      String subject,
      String enrollment) {}
}
