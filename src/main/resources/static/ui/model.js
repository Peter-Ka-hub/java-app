(function (root, factory) {
  "use strict";
  const model = factory();
  if (typeof module === "object" && module.exports) module.exports = model;
  else root.DiaryModel = model;
})(globalThis, function () {
  "use strict";

  const labelsByField = {
    type: { PRIMARY: "Podstawowa", SECONDARY: "Ponadpodstawowa", TERTIARY: "Wyższa" },
    role: { ADMIN: "Administrator", TEACHER: "Nauczyciel", STUDENT: "Uczeń" },
    status: { ACTIVE: "Aktywny", WITHDRAWN: "Wypisany" },
    action: { CREATED: "Dodanie", UPDATED: "Korekta", DELETED: "Usunięcie" }
  };
  const enumLabels = Object.assign(Object.create(null), ...Object.values(labelsByField));
  const resources = {
    schools: resource("Szkoły", "Szkoła", "Placówki i ich dane organizacyjne.", "school", "Nowa szkoła", [
      ["name", "Nazwa"], ["address", "Adres"], ["type", "Typ"], ["dateOfConstruction", "Data budowy"]
    ]),
    users: resource("Użytkownicy", "Użytkownik", "Konta, role i dostęp do dziennika.", "users", "Dodaj konto", [
      ["firstName", "Imię"], ["lastName", "Nazwisko"], ["email", "E-mail"], ["role", "Rola"], ["schoolId", "Szkoła"], ["enabled", "Dostęp"]
    ]),
    "academic-years": resource("Lata szkolne", "Rok szkolny", "Terminy, w których odbywa się nauka.", "calendar", "Nowy rok szkolny", [
      ["name", "Nazwa"], ["schoolId", "Szkoła"], ["startDate", "Początek"], ["endDate", "Koniec"]
    ]),
    classes: resource("Klasy", "Klasa", "Klasy przypisane do szkoły i roku szkolnego.", "class", "Nowa klasa", [
      ["name", "Nazwa"], ["schoolId", "Szkoła"], ["academicYearId", "Rok szkolny"]
    ]),
    subjects: resource("Przedmioty", "Przedmiot", "Zajęcia, prowadzący i ich uczestnicy.", "book", "Nowy przedmiot", [
      ["name", "Nazwa"], ["teacherId", "Prowadzący"], ["classId", "Klasa"], ["academicYearId", "Rok szkolny"], ["studentIds", "Aktywni uczniowie"]
    ]),
    enrollments: resource("Zapisy", "Zapis", "Uczestnictwo uczniów i daty wypisania.", "enrollment", "Zapisz ucznia", [
      ["studentId", "Uczeń"], ["subjectId", "Przedmiot"], ["joinedOn", "Data zapisu"], ["status", "Status"], ["leftOn", "Data wypisania"]
    ]),
    grades: resource("Oceny", "Ocena", "Oceny i pełna historia ich zmian.", "grade", "Wystaw ocenę", [
      ["studentId", "Uczeń"], ["subjectId", "Przedmiot"], ["score", "Ocena"], ["dateOfScore", "Data oceny"], ["gradedById", "Wystawił"]
    ])
  };
  for (const [key, definition] of Object.entries(resources)) definition.endpoint = "/api/" + key;

  function resource(title, singular, description, icon, createLabel, columns) {
    return { title, singular, description, icon, createLabel, columns: columns.map(([key, label]) => ({ key, label })) };
  }

  function isGlobalAdmin(actor) {
    return !!actor && actor.role === "ADMIN" && !actor.schoolId;
  }

  function rows(refs, key) {
    const value = refs && refs[key];
    return Array.isArray(value) ? value : [];
  }

  function lookup(refs, key, id) {
    if (key === "users" && refs && refs.actor && refs.actor.id === id) return refs.actor;
    return rows(refs, key).find(row => row.id === id);
  }

  function labelFor(key, id, refs) {
    if (!id) return key === "schools" ? "Administrator globalny" : "—";
    const row = lookup(refs, key, id);
    if (!row) {
      return { users: "Niedostępne konto", schools: "Niedostępna szkoła", subjects: "Niedostępny przedmiot", classes: "Niedostępna klasa", "academic-years": "Niedostępny rok" }[key] || "Niedostępny rekord";
    }
    if (key === "users") return [row.firstName, row.lastName].filter(Boolean).join(" ") || row.email;
    return row.name || "Bez nazwy";
  }

  function contextualOptions(options, type, refs) {
    const base = options.map(row => ({ value: row.id, label: labelFor(type, row.id, refs) }));
    const counts = new Map();
    for (const option of base) counts.set(option.label, (counts.get(option.label) || 0) + 1);
    const knownLabel = (key, id) => lookup(refs, key, id) ? labelFor(key, id, refs) : null;
    const contextual = base.map((option, index) => {
      if (counts.get(option.label) === 1) return option;
      const row = options[index];
      let details = [];
      if (type === "users") details = [row.email];
      if (type === "subjects") {
        const schoolClass = lookup(refs, "classes", row.classId);
        const yearId = row.academicYearId || (schoolClass && schoolClass.academicYearId);
        const year = lookup(refs, "academic-years", yearId);
        details = [
          knownLabel("classes", row.classId),
          knownLabel("academic-years", yearId),
          knownLabel("schools", row.schoolId || (year && year.schoolId)),
          knownLabel("users", row.teacherId)
        ];
      }
      if (type === "classes") details = [knownLabel("academic-years", row.academicYearId), knownLabel("schools", row.schoolId)];
      if (type === "academic-years") details = [knownLabel("schools", row.schoolId)];
      if (type === "schools") details = [row.address];
      const context = [...new Set(details.filter(Boolean))];
      return { value: option.value, label: [option.label, ...context].join(" · ") };
    });

    // Some separate records can share every visible field. Distinguish their current
    // positions without exposing identifiers or guessing unavailable reference data.
    const remaining = new Map();
    for (const option of contextual) remaining.set(option.label, (remaining.get(option.label) || 0) + 1);
    const positions = new Map();
    return contextual.map((option, index) => {
      if (counts.get(base[index].label) === 1 || remaining.get(option.label) === 1) return option;
      const position = (positions.get(option.label) || 0) + 1;
      positions.set(option.label, position);
      return { value: option.value, label: option.label + " · pozycja " + position };
    });
  }

  function formatDate(value) {
    if (!value) return "—";
    const parsed = new Date(/^\d{4}-\d{2}-\d{2}$/.test(value) ? value + "T12:00:00" : value);
    if (Number.isNaN(parsed.getTime())) return "—";
    return new Intl.DateTimeFormat("pl-PL", { day: "2-digit", month: "2-digit", year: "numeric" }).format(parsed);
  }

  function formatValue(key, field, value, refs) {
    if (field === "schoolId") return value ? labelFor("schools", value, refs) : "Administrator globalny";
    const related = { studentId: "users", teacherId: "users", gradedById: "users", changedById: "users", subjectId: "subjects", academicYearId: "academic-years", classId: "classes" };
    if (related[field]) return labelFor(related[field], value, refs);
    if (field === "studentIds") return String(Array.isArray(value) ? value.length : 0);
    if (field === "enabled") return value ? "Aktywny" : "Wyłączony";
    if (["dateOfConstruction", "dateOfBirth", "startDate", "endDate", "joinedOn", "leftOn", "dateOfScore", "createdAt", "updatedAt", "changedAt"].includes(field)) return formatDate(value);
    if (value === null || value === undefined || value === "") return "—";
    if (Object.hasOwn(labelsByField, field) && Object.hasOwn(labelsByField[field], value)) return labelsByField[field][value];
    return String(value);
  }

  function ownedSchool(actor, schoolId) {
    return isGlobalAdmin(actor) || (!!actor && !!schoolId && actor.schoolId === schoolId);
  }

  function canCreate(key, actor) {
    if (!actor || !resources[key]) return false;
    if (key === "schools") return isGlobalAdmin(actor);
    if (["grades", "enrollments"].includes(key)) return ["ADMIN", "TEACHER"].includes(actor.role);
    return actor.role === "ADMIN";
  }

  function canEdit(key, actor, record, refs) {
    if (!actor || !record || !resources[key]) return false;
    if (key === "schools") return actor.role === "ADMIN" && ownedSchool(actor, record.id);
    if (["grades", "enrollments"].includes(key)) {
      if (isGlobalAdmin(actor)) return true;
      const subject = lookup(refs, "subjects", record.subjectId);
      if (!subject || !ownedSchool(actor, subject.schoolId)) return false;
      return actor.role === "ADMIN" || (actor.role === "TEACHER" && subject.teacherId === actor.id);
    }
    return actor.role === "ADMIN" && ownedSchool(actor, record.schoolId);
  }

  function canDelete(key, actor, record, refs) {
    if (key === "enrollments") return false;
    if (key === "schools") return isGlobalAdmin(actor);
    if (key === "users" && actor && record && actor.id === record.id) return false;
    return canEdit(key, actor, record, refs);
  }

  function localDate(offset) {
    const date = new Date();
    date.setDate(date.getDate() + (offset || 0));
    return [date.getFullYear(), String(date.getMonth() + 1).padStart(2, "0"), String(date.getDate()).padStart(2, "0")].join("-");
  }

  function formFields(key, record, actor, refs, values) {
    record = record || null;
    refs = refs || {};
    values = values || {};
    const current = (name, fallback) => Object.prototype.hasOwnProperty.call(values, name) ? values[name] : record && record[name] !== undefined ? record[name] : fallback;
    const schoolId = current("schoolId", actor && actor.schoolId || "");
    const yearId = current("academicYearId", "");
    const subjectId = current("subjectId", "");
    const allSchools = rows(refs, "schools").filter(row => ownedSchool(actor, row.id));
    const eligibleSubjects = rows(refs, "subjects").filter(row => ownedSchool(actor, row.schoolId) && (actor.role !== "TEACHER" || row.teacherId === actor.id));
    const selectedSubject = eligibleSubjects.find(row => row.id === subjectId);
    const userRows = rows(refs, "users");
    const optionRows = (options, type) => contextualOptions(options, type, refs);
    const text = (name, label, extra) => Object.assign({ name, label, type: "text", required: true, value: current(name, ""), maxLength: 50 }, extra || {});
    const date = (name, label, extra) => Object.assign(text(name, label), { type: "date", maxLength: undefined }, extra || {});
    const select = (name, label, options, extra) => {
      const candidate = current(name, "");
      const valid = options.some(option => String(option.value) === String(candidate));
      return Object.assign({ name, label, type: "select", required: true, options, value: valid ? candidate : "" }, extra || {});
    };
    const schoolField = () => select("schoolId", "Szkoła", optionRows(allSchools, "schools"), { value: allSchools.some(row => row.id === schoolId) ? schoolId : "" });
    const enumOptions = list => list.map(value => ({ value, label: enumLabels[value] || value }));

    switch (key) {
      case "schools":
        return [text("name", "Nazwa szkoły"), text("address", "Adres", { maxLength: 200 }), date("dateOfConstruction", "Data budowy", { max: localDate() }), select("type", "Typ szkoły", enumOptions(["PRIMARY", "SECONDARY", "TERTIARY"]))];
      case "users": {
        const role = current("role", "STUDENT");
        const roleOptions = enumOptions(["STUDENT", "TEACHER", "ADMIN"]);
        return [
          text("firstName", "Imię"), text("lastName", "Nazwisko"), text("email", "E-mail", { type: "email" }),
          select("role", "Rola", roleOptions, { value: role }),
          date("dateOfBirth", "Data urodzenia", { max: localDate(-1) }),
          Object.assign(schoolField(), { required: role !== "ADMIN" || !isGlobalAdmin(actor), help: role === "ADMIN" && isGlobalAdmin(actor) ? "Bez szkoły konto będzie administratorem globalnym." : "Konto należy do jednej szkoły." }),
          text("password", record ? "Nowe hasło" : "Hasło", { type: "password", value: "", required: !record, minLength: 12, maxLength: 128, autocomplete: "new-password", help: record ? "Pozostaw puste, aby zachować hasło. Zmiana odwoła wcześniejsze sesje." : "Od 12 do 128 znaków." }),
          { name: "enabled", label: "Konto aktywne", type: "checkbox", value: current("enabled", true), required: false }
        ];
      }
      case "academic-years":
        return [text("name", "Nazwa roku", { placeholder: "np. 2026/2027" }), schoolField(), date("startDate", "Początek roku"), date("endDate", "Koniec roku", { min: current("startDate", "") })];
      case "classes":
        return [text("name", "Nazwa klasy", { placeholder: "np. 1A" }), schoolField(), select("academicYearId", "Rok szkolny", optionRows(rows(refs, "academic-years").filter(row => row.schoolId === schoolId), "academic-years"), { help: "Najpierw wybierz szkołę." })];
      case "subjects":
        return [
          text("name", "Nazwa przedmiotu"), schoolField(),
          select("teacherId", "Prowadzący", optionRows(userRows.filter(row => row.role === "TEACHER" && row.enabled && row.schoolId === schoolId), "users")),
          select("academicYearId", "Rok szkolny", optionRows(rows(refs, "academic-years").filter(row => row.schoolId === schoolId), "academic-years")),
          select("classId", "Klasa", optionRows(rows(refs, "classes").filter(row => row.schoolId === schoolId && row.academicYearId === yearId), "classes"), { required: false, help: "Opcjonalna. Klasa musi należeć do wybranego roku." })
        ];
      case "enrollments":
        if (record) return [
          select("subjectId", "Przedmiot", [{ value: record.subjectId, label: labelFor("subjects", record.subjectId, refs) }], { value: record.subjectId, disabled: true }),
          select("studentId", "Uczeń", [{ value: record.studentId, label: labelFor("users", record.studentId, refs) }], { value: record.studentId, disabled: true }),
          date("joinedOn", "Data zapisu", { value: record.joinedOn, readOnly: true }),
          select("status", "Status", enumOptions(["ACTIVE", "WITHDRAWN"])),
          date("leftOn", "Data wypisania", { required: false, disabled: current("status", record.status) === "ACTIVE", value: current("status", record.status) === "ACTIVE" ? "" : current("leftOn", ""), min: record.joinedOn, max: localDate(), help: "Przy wypisaniu pusta data oznacza dzisiejszy dzień lub koniec roku, jeśli już minął." })
        ];
        return [
          select("subjectId", "Przedmiot", optionRows(eligibleSubjects, "subjects")),
          select("studentId", "Uczeń", optionRows(userRows.filter(row => row.role === "STUDENT" && row.enabled && selectedSubject && row.schoolId === selectedSubject.schoolId && !rows(refs, "enrollments").some(enrollment => enrollment.subjectId === subjectId && enrollment.studentId === row.id)), "users"), { help: "Wybierz przedmiot. Uczeń z istniejącym zapisem wymaga edycji tego zapisu, a nie ponownego dodania." }),
          date("joinedOn", "Data zapisu", { value: current("joinedOn", localDate()), max: localDate() })
        ];
      case "grades": {
        const activeStudents = new Set(rows(refs, "enrollments").filter(row => row.subjectId === subjectId && row.status === "ACTIVE").map(row => row.studentId));
        return [
          record ? select("subjectId", "Przedmiot", [{ value: record.subjectId, label: labelFor("subjects", record.subjectId, refs) }], { value: record.subjectId, disabled: true }) : select("subjectId", "Przedmiot", optionRows(eligibleSubjects, "subjects")),
          record ? select("studentId", "Uczeń", [{ value: record.studentId, label: labelFor("users", record.studentId, refs) }], { value: record.studentId, disabled: true }) : select("studentId", "Uczeń", optionRows(userRows.filter(row => row.role === "STUDENT" && row.enabled && activeStudents.has(row.id) && selectedSubject && row.schoolId === selectedSubject.schoolId), "users"), { help: "Dostępni są aktywni uczniowie z aktywnym zapisem na wybrany przedmiot." }),
          select("score", "Ocena", enumOptions(["A", "B", "C", "D", "E", "F"])),
          date("dateOfScore", "Data oceny", { value: current("dateOfScore", localDate()), max: localDate(), help: "Data musi mieścić się w roku szkolnym i okresie zapisu ucznia." })
        ];
      }
      default: return [];
    }
  }

  function buildPayload(key, values, record) {
    values = values || {};
    const get = name => Object.prototype.hasOwnProperty.call(values, name) ? values[name] : record && record[name];
    const trim = name => String(get(name) === undefined || get(name) === null ? "" : get(name)).trim();
    const nullable = name => trim(name) || null;
    let payload;
    switch (key) {
      case "schools": payload = { name: trim("name"), address: trim("address"), dateOfConstruction: trim("dateOfConstruction"), type: trim("type") }; break;
      case "users":
        payload = { firstName: trim("firstName"), lastName: trim("lastName"), email: trim("email").toLowerCase(), role: trim("role"), dateOfBirth: trim("dateOfBirth"), schoolId: nullable("schoolId") };
        if (get("enabled") !== undefined) payload.enabled = get("enabled") === true || ["true", "on", "1"].includes(String(get("enabled")));
        if (!record || (typeof get("password") === "string" && get("password").trim())) payload.password = get("password") || "";
        break;
      case "academic-years": payload = { name: trim("name"), schoolId: trim("schoolId"), startDate: trim("startDate"), endDate: trim("endDate") }; break;
      case "classes": payload = { name: trim("name"), schoolId: trim("schoolId"), academicYearId: trim("academicYearId") }; break;
      case "subjects": payload = { name: trim("name"), teacherId: trim("teacherId"), schoolId: trim("schoolId"), academicYearId: trim("academicYearId"), classId: nullable("classId") }; break;
      case "enrollments":
        payload = record ? { status: trim("status"), leftOn: trim("status") === "ACTIVE" ? null : nullable("leftOn") } : { subjectId: trim("subjectId"), studentId: trim("studentId"), joinedOn: trim("joinedOn") };
        break;
      case "grades": payload = { subjectId: record ? record.subjectId : trim("subjectId"), studentId: record ? record.studentId : trim("studentId"), score: trim("score"), dateOfScore: trim("dateOfScore") }; break;
      default: throw new Error("Nieznany obszar dziennika.");
    }
    if (record) {
      if (!Number.isSafeInteger(record.version) || record.version < 0) throw new Error("Brak aktualnej wersji rekordu. Odśwież dane przed zapisem.");
      payload.version = record.version;
    }
    return payload;
  }

  return { resources, enumLabels, isGlobalAdmin, canCreate, canEdit, canDelete, formFields, buildPayload, formatValue, formatDate, localDate, labelFor, optionRows: contextualOptions };
});
