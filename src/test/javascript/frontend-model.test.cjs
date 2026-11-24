const { test } = require("node:test");
const assert = require("node:assert/strict");
const model = require("../../main/resources/static/ui/model.js");

const globalAdmin = { id: "global", role: "ADMIN", schoolId: null };
const admin = { id: "admin", role: "ADMIN", schoolId: "school-a" };
const teacher = { id: "teacher-a", role: "TEACHER", schoolId: "school-a" };
const student = { id: "student-a", role: "STUDENT", schoolId: "school-a" };
const refs = {
  schools: [{ id: "school-a", name: "Szkoła A" }, { id: "school-b", name: "Szkoła B" }],
  users: [
    { ...teacher, firstName: "Anna", lastName: "Nowak", enabled: true },
    { id: "teacher-b", role: "TEACHER", schoolId: "school-b", firstName: "Obcy", lastName: "Nauczyciel", enabled: true },
    { id: "inactive", role: "TEACHER", schoolId: "school-a", enabled: false },
    { ...student, firstName: "Jan", lastName: "Kowalski", enabled: true },
    { id: "student-b", role: "STUDENT", schoolId: "school-a", enabled: true },
    { id: "student-c", role: "STUDENT", schoolId: "school-b", enabled: true }
  ],
  "academic-years": [
    { id: "year-a", name: "2026/2027", schoolId: "school-a" },
    { id: "year-b", name: "2026/2027 B", schoolId: "school-b" }
  ],
  classes: [
    { id: "class-a", name: "1A", schoolId: "school-a", academicYearId: "year-a" },
    { id: "class-b", name: "1B", schoolId: "school-b", academicYearId: "year-b" }
  ],
  subjects: [
    { id: "subject-a", name: "Matematyka", teacherId: "teacher-a", schoolId: "school-a" },
    { id: "subject-b", name: "Fizyka", teacherId: "teacher-b", schoolId: "school-b" }
  ],
  enrollments: [
    { subjectId: "subject-a", studentId: "student-a", status: "ACTIVE" },
    { subjectId: "subject-a", studentId: "student-b", status: "WITHDRAWN" },
    { subjectId: "subject-b", studentId: "student-c", status: "ACTIVE" }
  ]
};
const fields = (key, actor, values = {}, record = null) => Object.fromEntries(model.formFields(key, record, actor, refs, values).map(field => [field.name, field]));

test("only the global administrator creates/deletes schools; students are read-only", () => {
  assert.equal(model.canCreate("schools", globalAdmin), true);
  for (const actor of [admin, teacher, student, null]) assert.equal(model.canCreate("schools", actor), false);
  assert.equal(model.canEdit("schools", admin, { id: "school-a" }, refs), true);
  assert.equal(model.canEdit("schools", admin, { id: "school-b" }, refs), false);
  assert.equal(model.canDelete("schools", admin, { id: "school-a" }, refs), false);
  for (const key of Object.keys(model.resources)) {
    assert.equal(model.canCreate(key, student), false);
    assert.equal(model.canEdit(key, student, { id: "x", schoolId: "school-a", subjectId: "subject-a" }, refs), false);
    assert.equal(model.canDelete(key, student, { id: "x", schoolId: "school-a", subjectId: "subject-a" }, refs), false);
  }
});

test("grade/enrollment controls require the assigned teacher and matching school", () => {
  assert.equal(model.canEdit("grades", teacher, { subjectId: "subject-a" }, refs), true);
  assert.equal(model.canEdit("grades", teacher, { subjectId: "subject-b" }, refs), false);
  assert.equal(model.canEdit("grades", admin, { subjectId: "subject-b" }, refs), false);
  assert.equal(model.canEdit("grades", teacher, { subjectId: "unknown" }, refs), false);
  assert.equal(model.canDelete("enrollments", globalAdmin, { subjectId: "subject-a" }, refs), false);
  assert.equal(model.canDelete("users", admin, { id: admin.id, schoolId: admin.schoolId }, refs), false);
});

test("cascading subject options never keep a teacher/year/class from another school", () => {
  const initial = fields("subjects", globalAdmin, { schoolId: "school-a", academicYearId: "year-a", teacherId: "teacher-a", classId: "class-a" });
  assert.deepEqual(initial.teacherId.options.map(option => option.value), ["teacher-a"]);
  assert.deepEqual(initial.classId.options.map(option => option.value), ["class-a"]);
  const changed = fields("subjects", globalAdmin, { schoolId: "school-b", academicYearId: "year-a", teacherId: "teacher-a", classId: "class-a" });
  assert.equal(changed.teacherId.value, "");
  assert.equal(changed.academicYearId.value, "");
  assert.equal(changed.classId.value, "");
  assert.deepEqual(changed.teacherId.options.map(option => option.value), ["teacher-b"]);
});

test("new grades offer only active enrolled students, and teachers only their subjects", () => {
  const grade = fields("grades", teacher, { subjectId: "subject-a" });
  assert.deepEqual(grade.subjectId.options.map(option => option.value), ["subject-a"]);
  assert.deepEqual(grade.studentId.options.map(option => option.value), ["student-a"]);
  assert.deepEqual(fields("grades", teacher).studentId.options, []);
  const enrollment = fields("enrollments", admin, { subjectId: "subject-a" });
  assert.deepEqual(enrollment.studentId.options, []);
});

test("user updates preserve version and omit empty password without stripping valid password spaces", () => {
  const record = { firstName: "Jan", lastName: "Kowalski", email: "JAN@TEST.LOCAL", role: "STUDENT", dateOfBirth: "2000-01-01", schoolId: "school-a", enabled: true, version: 3 };
  const payload = model.buildPayload("users", { firstName: " Jan ", password: "  ", version: 99, enabled: false }, record);
  assert.equal(payload.version, 3);
  assert.equal(payload.firstName, "Jan");
  assert.equal(payload.email, "jan@test.local");
  assert.equal(payload.enabled, false);
  assert.equal(Object.hasOwn(payload, "password"), false);
  assert.equal(model.buildPayload("users", { password: " Long-password-123! " }, record).password, " Long-password-123! ");
  assert.equal(model.buildPayload("users", { role: "ADMIN", schoolId: "", password: "Password-123456!" }).schoolId, null);
  assert.throws(() => model.buildPayload("users", {}, { ...record, version: undefined }), /Brak aktualnej wersji/);
});

test("grade corrections cannot be reassigned through form values", () => {
  const record = { subjectId: "subject-a", studentId: "student-a", score: "B", dateOfScore: "2026-01-01", version: 0 };
  assert.deepEqual(model.buildPayload("grades", { subjectId: "subject-b", studentId: "student-c", score: "A", version: 8 }, record), {
    subjectId: "subject-a", studentId: "student-a", score: "A", dateOfScore: "2026-01-01", version: 0
  });
  const correction = fields("grades", teacher, {}, record);
  assert.equal(correction.subjectId.disabled, true);
  assert.equal(correction.studentId.disabled, true);
});

test("enrollment updates send only lifecycle fields and clear a stale withdrawal date on activation", () => {
  const record = { subjectId: "subject-a", studentId: "student-a", joinedOn: "2026-01-01", status: "WITHDRAWN", leftOn: "2026-02-01", version: 2 };
  assert.deepEqual(model.buildPayload("enrollments", { subjectId: "subject-b", status: "ACTIVE", version: 9 }, record), { status: "ACTIVE", leftOn: null, version: 2 });
  assert.deepEqual(model.buildPayload("enrollments", { status: "WITHDRAWN", leftOn: "" }, record), { status: "WITHDRAWN", leftOn: null, version: 2 });
  assert.deepEqual(model.buildPayload("enrollments", { subjectId: "subject-a", studentId: "student-a", joinedOn: "2026-01-01", status: "WITHDRAWN" }), { subjectId: "subject-a", studentId: "student-a", joinedOn: "2026-01-01" });
});

test("labels use authorized reference names and the current actor, not raw UUIDs", () => {
  assert.equal(model.labelFor("users", "student-a", refs), "Jan Kowalski");
  assert.equal(model.labelFor("users", "hidden-uuid", refs), "Niedostępne konto");
  assert.equal(model.formatValue("users", "schoolId", null, refs), "Administrator globalny");
  assert.equal(model.formatValue("subjects", "studentIds", ["one", "two"], refs), "2");
  assert.equal(model.formatValue("grades", "dateOfScore", "2026-01-02", refs), "02.01.2026");
  assert.equal(model.labelFor("users", "me", { actor: { id: "me", firstName: "Moje", lastName: "Konto" } }), "Moje Konto");
});

test("table text stays literal even when it matches an enum or a JavaScript property", () => {
  for (const name of ["ADMIN", "ACTIVE", "constructor", "__proto__", "toString"]) {
    assert.equal(model.formatValue("schools", "name", name, refs), name);
    assert.equal(model.formatValue("subjects", "name", name, refs), name);
  }
  assert.equal(model.formatValue("schools", "type", "PRIMARY", refs), "Podstawowa");
  assert.equal(model.formatValue("users", "role", "ADMIN", refs), "Administrator");
  assert.equal(model.formatValue("enrollments", "status", "ACTIVE", refs), "Aktywny");
  assert.equal(model.formatValue("enrollments", "status", "ADMIN", refs), "ADMIN");
  assert.equal(model.formatValue("schools", "type", "constructor", refs), "constructor");
});

test("teachers with identical names are distinguished by email while a unique label stays unchanged", () => {
  const teachers = [
    { ...teacher, firstName: "Jan", lastName: "Kowalski", email: "jan.pierwszy@example.test", enabled: true },
    { ...teacher, id: "another-teacher", firstName: "Jan", lastName: "Kowalski", email: "jan.drugi@example.test", enabled: true },
    { ...teacher, id: "unique-teacher", firstName: "Anna", lastName: "Nowak", email: "anna@example.test", enabled: true }
  ];
  const scopedRefs = { ...refs, users: teachers };
  const form = model.formFields("subjects", null, admin, scopedRefs, { schoolId: "school-a" });
  const choices = form.find(field => field.name === "teacherId").options;
  assert.deepEqual(choices.map(option => option.label), [
    "Jan Kowalski · jan.pierwszy@example.test",
    "Jan Kowalski · jan.drugi@example.test",
    "Anna Nowak"
  ]);
  assert.equal(model.labelFor("users", teachers[0].id, scopedRefs), "Jan Kowalski");
  const literalContextName = { ...teachers[2], firstName: "Jan", lastName: "Kowalski · jan.pierwszy@example.test" };
  const collidingNames = [teachers[0], teachers[1], literalContextName];
  const disambiguated = model.optionRows(collidingNames, "users", { users: collidingNames });
  assert.equal(disambiguated[2].label, "Jan Kowalski · jan.pierwszy@example.test");
  assert.equal(new Set(disambiguated.map(option => option.label)).size, 3);
});

test("same-named subjects and classes carry their visible year and school context", () => {
  const contextualRefs = {
    ...refs,
    "academic-years": [
      { id: "year-first-id", name: "2026/2027", schoolId: "school-a" },
      { id: "year-second-id", name: "2027/2028", schoolId: "school-a" }
    ],
    classes: [
      { id: "class-first-id", name: "1A", schoolId: "school-a", academicYearId: "year-first-id" },
      { id: "class-second-id", name: "1A", schoolId: "school-a", academicYearId: "year-second-id" }
    ],
    subjects: [
      { id: "subject-first-id", name: "Matematyka", schoolId: "school-a", classId: "class-first-id", academicYearId: "year-first-id", teacherId: teacher.id },
      { id: "subject-second-id", name: "Matematyka", schoolId: "school-a", classId: "class-second-id", academicYearId: "year-second-id", teacherId: teacher.id },
      { id: "subject-unique-id", name: "Biologia", schoolId: "school-a", teacherId: teacher.id }
    ]
  };
  const form = model.formFields("grades", null, globalAdmin, contextualRefs);
  const choices = form.find(field => field.name === "subjectId").options;
  assert.deepEqual(choices.map(option => option.label), [
    "Matematyka · 1A · 2026/2027 · Szkoła A · Anna Nowak",
    "Matematyka · 1A · 2027/2028 · Szkoła A · Anna Nowak",
    "Biologia"
  ]);
  assert.deepEqual(model.optionRows(contextualRefs.classes, "classes", contextualRefs).map(option => option.label), [
    "1A · 2026/2027 · Szkoła A", "1A · 2027/2028 · Szkoła A"
  ]);
  assert.equal(model.formatValue("subjects", "subjectId", "subject-first-id", contextualRefs), "Matematyka");
});

test("duplicate years use school names and missing context never exposes identifiers", () => {
  const years = [
    { id: "first-secret-uuid", name: "2026/2027", schoolId: "school-a" },
    { id: "second-secret-uuid", name: "2026/2027", schoolId: "school-b" }
  ];
  const available = { ...refs, "academic-years": years };
  assert.deepEqual(model.optionRows(years, "academic-years", available).map(option => option.label), [
    "2026/2027 · Szkoła A", "2026/2027 · Szkoła B"
  ]);
  const hidden = { "academic-years": years };
  const choices = model.optionRows(years, "academic-years", hidden);
  assert.equal(new Set(choices.map(option => option.label)).size, 2);
  assert.ok(choices.every(option => !option.label.includes("uuid") && !option.label.includes("school-")));
});
