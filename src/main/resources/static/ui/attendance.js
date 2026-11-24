(function () {
  "use strict";

  const statuses = new Map([
    ["PRESENT", "Obecność"],
    ["ABSENT", "Nieobecność"],
    ["LATE", "Spóźnienie"]
  ]);
  const dateTime = new Intl.DateTimeFormat("pl-PL", { dateStyle: "medium", timeStyle: "short" });
  const percentage = new Intl.NumberFormat("pl-PL", { maximumFractionDigits: 2 });

  window.DiaryAttendance = function ({
    api, model, h, button, field, label, lookups, rows, beginModal, closeModal,
    setModalBusy, pageHeader, errorBox, notify, refreshData
  }) {
    let reportController;

    function cancelReport() {
      reportController?.abort();
      reportController = undefined;
    }

    function table(caption, columns, data) {
      return h("div", { class: "table-wrap" }, h("table", { class: "data-table" },
        h("caption", { class: "sr-only" }, caption),
        h("thead", {}, h("tr", {}, columns.map(title => h("th", { scope: "col" }, title)))),
        h("tbody", {}, data.map(cells => h("tr", {}, cells.map(cell => h("td", {}, cell)))))
      ));
    }

    function pagination(page, load) {
      if (page.totalPages < 2) return null;
      return h("div", { class: "pagination" },
        h("span", {}, `Strona ${page.page + 1} z ${page.totalPages}`),
        h("div", {},
          page.page > 0 && button("Poprzednia", () => load(page.page - 1)),
          page.page + 1 < page.totalPages && button("Następna", () => load(page.page + 1))));
    }

    function statusBadge(status) {
      const style = status === "PRESENT" ? "success" : status === "LATE" ? "warning" : "neutral";
      return h("span", { class: `badge badge-${style}` }, statuses.get(status) || "Nie oznaczono");
    }

    function openEnrollment(subject) {
      const refs = lookups();
      const actor = refs.actor;
      if (actor?.role !== "TEACHER") return;
      const eligible = rows("subjects").filter(item => model.canEdit("enrollments", actor, { subjectId: item.id }, refs));
      const { body, signal } = beginModal("Zapisz ucznia po e-mailu", "Podaj dokładny adres użyty przez ucznia przy rejestracji.", "attendance-enrollment");
      const subjectPicker = h("select", { id: "email-enrollment-subject", class: "input", required: true },
        h("option", { value: "" }, "Wybierz przedmiot…"),
        model.optionRows(eligible, "subjects", refs).map(option => h("option", { value: option.value }, option.label)));
      subjectPicker.value = subject?.id || (eligible.length === 1 ? eligible[0].id : "");
      const email = h("input", { id: "email-enrollment-address", type: "email", class: "input", required: true, maxlength: 50 });
      const date = h("input", { id: "email-enrollment-date", type: "date", class: "input", required: true, value: model.localDate(), max: model.localDate() });
      const errors = h("div", {});
      const save = h("button", { type: "submit", class: "btn btn-primary" }, "Zapisz ucznia");
      const form = h("form", { "aria-label": "Zapis po e-mailu" }, errors,
        field("Przedmiot", subjectPicker), field("Adres e-mail ucznia", email), field("Data zapisu", date),
        h("div", { class: "dialog-footer" }, button("Anuluj", closeModal), save));
      form.addEventListener("submit", async event => {
        event.preventDefault();
        if (save.disabled || !form.reportValidity()) return;
        const payload = { email: email.value.trim(), joinedOn: date.value };
        const path = `/api/subjects/${encodeURIComponent(subjectPicker.value)}/enrollments/by-email`;
        errors.replaceChildren();
        setModalBusy(true);
        try {
          await api.request(path, { method: "POST", body: payload, signal });
        } catch (failure) {
          if (!signal.aborted) errors.replaceChildren(errorBox(failure));
          return;
        } finally {
          setModalBusy(false);
        }
        if (!signal.aborted) {
          closeModal();
          await refreshData("Zapisano ucznia na przedmiot.");
        }
      });
      body.append(form);
    }

    function openLessons(subject) {
      const actor = lookups().actor;
      if (!model.canEdit("enrollments", actor, { subjectId: subject.id }, lookups())) return;
      const { body, signal } = beginModal(`Frekwencja · ${subject.name}`, "Lekcję rejestrujesz w dniu jej odbycia. Osobny numer pozwala utworzyć kilka lekcji tego samego dnia.", "attendance-lessons");
      const path = `/api/subjects/${encodeURIComponent(subject.id)}/attendance-lessons`;
      const number = h("input", { id: "attendance-lesson-number", type: "number", class: "input", min: 1, max: 99, step: 1, required: true });
      const errors = h("div", {});
      const results = h("section", { "aria-label": "Lekcje przedmiotu", class: "attendance-results" });
      const save = h("button", { type: "submit", class: "btn btn-primary" }, "Zarejestruj lekcję");
      const form = h("form", { "aria-label": "Rejestracja lekcji" }, errors,
        h("div", { class: "attendance-filters" }, field("Numer lekcji", number), save));

      async function load(page = 0) {
        save.disabled = true;
        results.replaceChildren(h("p", { role: "status" }, "Wczytywanie lekcji…"));
        try {
          const response = await api.request(`${path}?page=${page}&size=10`, { signal });
          if (signal.aborted) return;
          const contents = response.items.length
            ? table("Lekcje przedmiotu", ["Data", "Numer", "Stan", "Działania"], response.items.map(lesson => [
              model.formatDate(lesson.lessonDate), lesson.lessonNumber,
              lesson.state === "FINALIZED" ? "Zatwierdzona" : "Szkic",
              button("Otwórz listę", () => openLesson(subject, lesson.id))
            ]))
            : h("p", { class: "helper" }, "Brak zarejestrowanych lekcji.");
          results.replaceChildren(contents);
          const controls = pagination(response, load);
          if (controls) results.append(controls);
        } catch (failure) {
          if (!signal.aborted) results.replaceChildren(errorBox(failure), button("Spróbuj ponownie", () => load(page)));
        } finally {
          save.disabled = false;
        }
      }

      form.addEventListener("submit", async event => {
        event.preventDefault();
        if (save.disabled || !form.reportValidity()) return;
        errors.replaceChildren();
        setModalBusy(true);
        let detail;
        try {
          detail = await api.request(path, { method: "POST", body: { lessonNumber: number.valueAsNumber }, signal });
        } catch (failure) {
          if (!signal.aborted) errors.replaceChildren(errorBox(failure));
        } finally {
          setModalBusy(false);
        }
        if (detail && !signal.aborted) openLesson(subject, detail.lesson.id, detail);
      });
      body.append(form);
      if (actor.role === "TEACHER") body.append(button("Zapisz ucznia po e-mailu", () => openEnrollment(subject)));
      body.append(results);
      load();
    }

    function openLesson(subject, lessonId, detail) {
      const { body, signal } = beginModal(`Lista obecności · ${subject.name}`, "Lista uczniów pochodzi z chwili rejestracji lekcji.", "attendance-lesson");
      const path = `/api/attendance/lessons/${encodeURIComponent(lessonId)}`;

      async function load() {
        body.replaceChildren(h("p", { role: "status" }, "Wczytywanie listy obecności…"));
        try {
          const current = await api.request(path, { signal });
          if (!signal.aborted) render(current);
        } catch (failure) {
          if (!signal.aborted) body.replaceChildren(errorBox(failure), button("Spróbuj ponownie", load), button("Wróć do lekcji", () => openLessons(subject)));
        }
      }

      function render({ lesson, entries }) {
        const finalized = lesson.state === "FINALIZED";
        const errors = h("div", {});
        const controls = [];
        let stale = false;
        const draft = !finalized && button("Zapisz szkic", () => save("DRAFT"));
        const submit = button(finalized ? "Zapisz korektę" : "Zatwierdź frekwencję", () => save("FINALIZED"), "primary");

        function updateButtons() {
          if (draft) draft.disabled = stale;
          const complete = controls.length > 0 && controls.every(({ input }) => input.value);
          const changed = controls.some(({ entry, input }) => input.value !== (entry.status || ""));
          submit.disabled = stale || !complete || (finalized && !changed);
        }

        async function save(nextState) {
          if (stale || (nextState === "FINALIZED" ? submit.disabled : draft.disabled)) return;
          const payload = {
            version: lesson.version, state: nextState,
            entries: controls.map(({ entry, input }) => ({ studentId: entry.studentId, status: input.value || null }))
          };
          errors.replaceChildren();
          setModalBusy(true);
          let current;
          try {
            current = await api.request(path, { method: "PUT", body: payload, signal });
          } catch (failure) {
            if (signal.aborted) return;
            if (failure.status === 409) {
              stale = true;
              errors.replaceChildren(
                h("p", { class: "form-error", role: "alert" }, "Ktoś zapisał nowszą wersję tej listy. Wczytaj ją, aby sprawdzić aktualne statusy i ponownie wprowadzić swoją zmianę."),
                button("Wczytaj aktualną listę", load, "primary"));
            } else errors.replaceChildren(errorBox(failure));
            errors.scrollIntoView({ block: "nearest" });
          } finally {
            setModalBusy(false);
            updateButtons();
          }
          if (current && !signal.aborted) {
            render(current);
            notify(finalized ? "Zapisano korektę." : nextState === "FINALIZED" ? "Zatwierdzono frekwencję." : "Zapisano szkic.");
          }
        }

        const data = entries.map(entry => {
          const name = `${entry.firstName} ${entry.lastName}`;
          const input = h("select", { class: "input attendance-status", "aria-label": `Status — ${name}` },
            !finalized && h("option", { value: "" }, "Nie oznaczono"),
            [...statuses].map(([value, title]) => h("option", { value }, title)));
          input.value = entry.status || "";
          input.addEventListener("change", updateButtons);
          controls.push({ entry, input });
          const corrections = entry.corrections.length
            ? h("details", { class: "attendance-corrections" },
              h("summary", {}, `Korekty (${entry.corrections.length})`),
              h("ul", {}, entry.corrections.map(change => h("li", {},
                `${statuses.get(change.previousStatus)} → ${statuses.get(change.newStatus)} · ${dateTime.format(new Date(change.changedAt))} · ${label("users", change.changedById)}`))))
            : "—";
          return [name, input, corrections];
        });
        body.replaceChildren(
          h("p", { class: "notice" }, `${model.formatDate(lesson.lessonDate)} · Lekcja ${lesson.lessonNumber} · ${finalized ? "Zatwierdzona" : "Szkic"}`),
          errors,
          table("Lista obecności", ["Uczeń", "Status", "Historia korekt"], data),
          !finalized && h("p", { class: "helper attendance-results" }, "Zatwierdzenie wymaga statusu dla każdego ucznia."),
          h("div", { class: "attendance-actions" }, button("Wróć do lekcji", () => openLessons(subject)), draft, submit)
        );
        updateButtons();
      }

      if (detail) render(detail);
      else load();
    }

    function renderReport(main, actor) {
      const admin = actor.role === "ADMIN";
      const global = model.isGlobalAdmin(actor);
      const from = h("input", { id: "attendance-from", class: "input", type: "date", required: true, value: model.localDate(-29) });
      const to = h("input", { id: "attendance-to", class: "input", type: "date", required: true, value: model.localDate() });
      const school = global && h("select", { id: "attendance-school", class: "input" }, h("option", { value: "" }, "Wszystkie szkoły"),
        model.optionRows(rows("schools"), "schools", lookups()).map(option => h("option", { value: option.value }, option.label)));
      const classFilter = admin && h("select", { id: "attendance-class", class: "input" });
      const subject = admin && h("select", { id: "attendance-subject", class: "input" });
      const results = h("section", { class: "panel attendance-results", "aria-label": "Wyniki frekwencji", "aria-live": "polite" });

      function populate(select, key, title) {
        const scope = school ? school.value : actor.schoolId;
        const options = model.optionRows(rows(key).filter(item => !scope || item.schoolId === scope), key, lookups());
        select.replaceChildren(h("option", { value: "" }, title), ...options.map(option => h("option", { value: option.value }, option.label)));
      }

      function updateFilters() {
        populate(classFilter, "classes", "Wszystkie klasy");
        populate(subject, "subjects", "Wszystkie przedmioty");
      }

      if (admin) updateFilters();
      if (school) school.addEventListener("change", updateFilters);
      const form = h("form", { class: "panel attendance-filters", "aria-label": "Filtry frekwencji" },
        field("Od", from), field("Do", to), school && field("Szkoła", school),
        admin && field("Klasa", classFilter), admin && field("Przedmiot", subject),
        h("button", { type: "submit", class: "btn btn-primary" }, "Pokaż zestawienie"));

      async function load(filters, page = 0) {
        cancelReport();
        reportController = new AbortController();
        const { signal } = reportController;
        const params = new URLSearchParams({ ...filters, page: String(page), size: "20" });
        const path = admin ? "/api/attendance/report" : "/api/attendance/me";
        results.replaceChildren(h("p", { role: "status" }, "Wczytywanie frekwencji…"));
        try {
          const { summary, entries } = await api.request(`${path}?${params}`, { signal });
          if (signal.aborted) return;
          const rate = summary.attendancePercentage == null ? "Brak danych" : `${percentage.format(summary.attendancePercentage)}%`;
          const counters = [["Obecności", summary.present], ["Nieobecności", summary.absent], ["Spóźnienia", summary.late], ["Procent obecności", rate]];
          const columns = admin ? ["Data", "Lekcja", "Przedmiot", "Szkoła", "Klasa", "Uczeń", "Status"] : ["Data", "Lekcja", "Przedmiot", "Status"];
          const data = entries.items.map(item => [model.formatDate(item.lessonDate), item.lessonNumber, item.subjectName,
            ...(admin ? [label("schools", item.schoolId), item.className || "—", `${item.firstName} ${item.lastName}`] : []), statusBadge(item.status)]);
          results.replaceChildren(
            h("h2", { class: "panel-title" }, `Zatwierdzone wpisy · ${model.formatDate(filters.from)} – ${model.formatDate(filters.to)}`),
            h("dl", { class: "stats-grid attendance-summary" }, counters.map(([title, count]) => h("div", { class: "stat-card" },
              h("dt", { class: "stat-label" }, title), h("dd", { class: "stat-value" }, count)))),
            h("p", { class: "helper" }, "Spóźnienia liczą się do procentu obecności i mają osobny licznik."),
            data.length ? table("Zatwierdzona frekwencja", columns, data) : h("p", { class: "helper" }, "Brak zatwierdzonych wpisów w tym okresie.")
          );
          const controls = pagination(entries, next => load(filters, next));
          if (controls) results.append(controls);
        } catch (failure) {
          if (!signal.aborted) results.replaceChildren(errorBox(failure));
        }
      }

      function applyFilters() {
        const filters = { from: from.value, to: to.value };
        if (school?.value) filters.schoolId = school.value;
        if (classFilter?.value) filters.classId = classFilter.value;
        if (subject?.value) filters.subjectId = subject.value;
        load(filters);
      }

      form.addEventListener("submit", event => {
        event.preventDefault();
        if (form.reportValidity()) applyFilters();
      });
      main.replaceChildren(pageHeader("Frekwencja", admin
        ? "Przejrzyj zatwierdzoną frekwencję. Okres może obejmować do 366 dni."
        : "Przejrzyj swoją historię frekwencji i podsumowanie za wybrany okres."), form, results);
      applyFilters();
    }

    return { openEnrollment, openLessons, renderReport, cancelReport };
  };
})();
