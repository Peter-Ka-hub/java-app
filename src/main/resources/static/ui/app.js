(function () {
  "use strict";

  const model = window.DiaryModel;
  const app = document.getElementById("app");
  const modal = document.getElementById("modal");
  const toast = document.getElementById("toast");
  const keys = Object.keys(model.resources);
  const collator = new Intl.Collator("pl", { numeric: true, sensitivity: "base" });
  const basePath = new URL("./", window.location.href).pathname;
  const invitationToken = /^[A-Za-z0-9_-]{43}$/;
  const invitationExpiry = new Intl.DateTimeFormat("pl-PL", { dateStyle: "medium", timeStyle: "short" });
  const invitationStatuses = new Map([
    ["PENDING", "Oczekuje"], ["ACCEPTED", "Wykorzystane"],
    ["REVOKED", "Unieważnione"], ["EXPIRED", "Wygasłe"]
  ]);
  const state = {
    actor: null, refs: {}, view: "dashboard", loading: false, error: null,
    query: "", page: 0, size: 12, sort: null, direction: 1, generation: 0
  };
  let controller = null;
  let modalController = null;
  let toastTimer;
  let lastFocus;
  let modalKind = null;
  let mutationPending = false;
  let logoutPending = false;
  const api = window.DiaryApi.createApi({ baseUrl: basePath, onUnauthorized: expired });
  const attendance = window.DiaryAttendance({
    api, model, h, button, field: invitationField, label, lookups, rows, beginModal,
    closeModal, setModalBusy, pageHeader, errorBox, notify, refreshData
  });

  function h(tag, attributes, ...children) {
    const element = document.createElement(tag);
    for (const [key, value] of Object.entries(attributes || {})) {
      if (value === null || value === undefined || value === false) continue;
      if (key === "class") element.className = value;
      else if (key === "text") element.textContent = value;
      else if (key.startsWith("on")) element.addEventListener(key.slice(2), value);
      else if (key === "value" || key === "checked" || key === "disabled" || key === "readOnly") element[key] = value;
      else element.setAttribute(key, value === true ? "" : String(value));
    }
    for (const child of children.flat(Infinity)) {
      if (child === null || child === undefined || child === false) continue;
      element.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
    return element;
  }

  const paths = {
    dashboard: ["M3 3h7v7H3z", "M14 3h7v7h-7z", "M3 14h7v7H3z", "M14 14h7v7h-7z"],
    school: ["M3 21h18M5 21V9l7-6 7 6v12M9 21v-6h6v6M9 10h.01M15 10h.01"],
    users: ["M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2", "M16 3a4 4 0 0 1 0 8M22 21v-2a4 4 0 0 0-3-3.87", "M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8"],
    calendar: ["M4 5h16v16H4zM16 3v4M8 3v4M4 11h16M8 15h2M14 15h2"],
    class: ["M3 4h18v13H3zM8 21h8M12 17v4M7 8h10M7 12h6"],
    book: ["M12 5v16M12 5C9 3 5 3 2 4v16c3-1 7-1 10 1 3-2 7-2 10-1V4c-3-1-7-1-10 1z"],
    enrollment: ["M9 5h11v16H4V5h3M8 3h8v4H8zM8 13l2 2 5-5"],
    grade: ["m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9z"],
    plus: ["M12 5v14M5 12h14"], arrow: ["M5 12h14m-6-6 6 6-6 6"],
    refresh: ["M20 7v5h-5M4 17v-5h5M6 7a7 7 0 0 1 11-2l3 3M4 16l3 3a7 7 0 0 0 11-2"],
    search: ["M21 21l-5-5", "M10.5 18a7.5 7.5 0 1 0 0-15 7.5 7.5 0 0 0 0 15"],
    edit: ["m16 3 5 5-12 12-6 1 1-6zM14 5l5 5"],
    trash: ["M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7M14 10v7"],
    history: ["M3 3v6h6M3 9a9 9 0 1 1 0 6M12 7v5l3 2"],
    logout: ["M9 3H4v18h5M10 12h11m-4-4 4 4-4 4"],
    close: ["m6 6 12 12M6 18 18 6"], menu: ["M3 6h18M3 12h18M3 18h18"],
    check: ["m5 12 4 4L19 6"], lock: ["M5 10h14v11H5zM8 10V6a4 4 0 0 1 8 0v4"],
    eye: ["M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7z", "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6"],
    chevron: ["m9 5 7 7-7 7"], info: ["M12 11v6M12 7h.01", "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20"]
  };

  function icon(name) {
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    for (const [key, value] of Object.entries({ class: "icon", viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", "stroke-width": "1.7", "stroke-linecap": "round", "stroke-linejoin": "round", "aria-hidden": "true" })) svg.setAttribute(key, value);
    for (const d of paths[name] || paths.book) {
      const path = document.createElementNS(svg.namespaceURI, "path");
      path.setAttribute("d", d);
      svg.append(path);
    }
    return svg;
  }

  function button(label, action, style = "secondary", name) {
    return h("button", { type: "button", class: `btn btn-${style}`, onclick: action }, name && icon(name), label);
  }
  function iconButton(label, name, action) {
    return h("button", { type: "button", class: "icon-btn", title: label, "aria-label": label, onclick: action }, icon(name));
  }
  function lookups() { return { ...state.refs, actor: state.actor }; }
  function rows(key) { return state.refs[key] || []; }
  function label(key, id) { return model.labelFor(key, id, lookups()); }
  function roleLabel() {
    return model.isGlobalAdmin(state.actor) ? "Administrator globalny" : model.enumLabels[state.actor?.role] || "";
  }
  function initials() { return `${state.actor?.firstName?.[0] || ""}${state.actor?.lastName?.[0] || ""}`.toLocaleUpperCase("pl-PL"); }
  function normalized(value) { return String(value).toLocaleLowerCase("pl-PL").normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/ł/g, "l"); }
  function notify(message) {
    clearTimeout(toastTimer);
    toast.replaceChildren(icon("check"), h("span", {}, message));
    toast.className = "is-open";
    toastTimer = setTimeout(() => { toast.className = ""; toast.replaceChildren(); }, 5000);
  }
  function errorBox(error) {
    const details = Array.isArray(error?.errors) ? error.errors : [];
    return h("div", { class: "form-error", role: "alert" }, h("strong", {}, error?.message || "Nie udało się pobrać danych."), details.length > 0 && h("ul", {}, details.map(item => h("li", {}, item))), error?.serverMessage && !details.length && [400, 409].includes(error.status) && h("p", {}, error.serverMessage));
  }

  function route() {
    const parts = location.hash.replace(/^#\/?/, "").split("/");
    if (parts[0] === "invite") {
      const token = parts.length === 2 && invitationToken.test(parts[1]) ? parts[1] : null;
      return { view: "invite", token };
    }
    if (parts[0] === "login") return { view: "login" };
    const view = keys.includes(parts[0]) || parts[0] === "attendance" ? parts[0] : "dashboard";
    const historyId = view === "grades" && parts[1] === "history" && /^[0-9a-f-]{36}$/i.test(parts[2] || "") ? parts[2] : null;
    return { view, historyId };
  }
  function navigate(view) {
    if (mutationPending) return;
    closeModal();
    if (location.hash === `#/${view}`) { state.query = ""; state.page = 0; renderMain(); }
    else location.hash = `#/${view}`;
    setMenu(false);
  }
  function onRoute() {
    if (mutationPending) return;
    const next = route();
    if (showPublicRoute(next)) return;
    if (state.view === "invite" || state.view === "login") {
      start();
      return;
    }
    if (modalKind === "history" && modal.dataset.historyId !== next.historyId) closeModal();
    if (state.view !== next.view) {
      if (modalKind?.startsWith("attendance-")) closeModal();
      state.query = "";
      state.page = 0;
      state.sort = null;
    }
    state.view = next.view;
    if (state.actor) {
      renderNavigation();
      renderMain();
      document.getElementById("main")?.focus({ preventScroll: true });
      if (next.historyId && !state.loading && !state.error) openHistory(next.historyId);
    }
  }
  window.addEventListener("hashchange", onRoute);

  function clearSessionView() {
    state.generation++;
    clearTimeout(toastTimer);
    toast.className = "";
    toast.replaceChildren();
    controller?.abort();
    attendance.cancelReport();
    state.actor = null;
    state.refs = {};
    state.error = null;
    state.loading = false;
    closeModal(true);
  }
  function resetSession(message) {
    if (showPublicRoute(route())) return;
    clearSessionView();
    history.replaceState(null, "", "#/dashboard");
    renderLogin(false, message);
  }
  function expired(error) {
    if (state.actor) resetSession(error.message);
  }

  function showPublicRoute(next) {
    if (next.view !== "invite" && next.view !== "login") return false;
    clearSessionView();
    state.view = next.view;
    if (next.view === "invite") renderActivation(next.token);
    else renderLogin();
    return true;
  }

  function renderLogin(checking = false, message = "") {
    app.replaceChildren();
    const error = h("div", { id: "login-error" }, message && h("div", { class: "notice", role: "status" }, message));
    const email = h("input", { id: "login-email", name: "email", type: "email", class: "input", required: true, autocomplete: "username", placeholder: "twoj.adres@szkola.pl", maxLength: 50 });
    const password = h("input", { id: "login-password", name: "password", type: "password", class: "input", required: true, autocomplete: "current-password", maxLength: 128, placeholder: "Wpisz hasło" });
    const show = iconButton("Pokaż hasło", "eye", () => {
      const visible = password.type === "password";
      password.type = visible ? "text" : "password";
      show.setAttribute("aria-label", visible ? "Ukryj hasło" : "Pokaż hasło");
      show.setAttribute("aria-pressed", String(visible));
    });
    const submit = h("button", { type: "submit", class: "btn btn-primary", disabled: checking }, checking ? "Sprawdzanie sesji…" : "Zaloguj się", icon("arrow"));
    const form = h("form", { "aria-label": "Logowanie do dziennika" },
      error,
      h("div", { class: "field" }, h("label", { for: email.id, class: "field-label" }, "Adres e-mail"), email),
      h("div", { class: "field" }, h("label", { for: password.id, class: "field-label" }, "Hasło"), h("div", { class: "input-wrap" }, password, show)), submit,
      h("p", { class: "helper" }, icon("lock"), "Korzystasz z konta przydzielonego przez administratora szkoły."));
    form.addEventListener("submit", async event => {
      event.preventDefault();
      if (submit.disabled || !form.reportValidity()) return;
      submit.disabled = true;
      submit.textContent = "Logowanie…";
      error.replaceChildren();
      try {
        const actor = await api.login(email.value.trim(), password.value);
        if (!form.isConnected || route().view === "invite") return;
        if (route().view === "login") history.replaceState(null, "", "#/dashboard");
        state.actor = actor;
        state.generation++;
        state.view = route().view;
        renderShell();
        await refreshData();
      } catch (failure) {
        error.replaceChildren(errorBox(failure));
        password.focus();
      } finally {
        password.value = "";
        submit.disabled = false;
        submit.replaceChildren("Zaloguj się", icon("arrow"));
      }
    });
    app.append(h("div", { class: "login-layout" },
      h("section", { class: "login-story", "aria-label": "O dzienniku" },
        h("div", { class: "login-brand brand" }, h("span", { class: "brand-mark" }, icon("book")), h("span", {}, h("strong", { class: "brand-name" }, "Dziennik"), h("small", { class: "brand-subtitle" }, "Twoja szkolna przestrzeń"))),
        h("div", { class: "login-hero" }, h("p", { class: "eyebrow" }, "MNIEJ FORMALNOŚCI. WIĘCEJ NAUKI."), h("h2", {}, "Cała szkoła.", h("br"), "Jeden dziennik."), h("p", { class: "lead" }, "Szkoły, klasy, przedmioty i oceny — uporządkowane w jednym miejscu.")),
        h("div", { class: "login-features" }, [["school", "Spójna organizacja", "Szkoły, lata szkolne i klasy."], ["enrollment", "Właściwe osoby", "Konta, przedmioty i zapisy uczniów."], ["history", "Pełna historia", "Oceny i każda wprowadzona korekta."]].map(([name, title, text]) => h("div", { class: "login-feature" }, icon(name), h("div", {}, h("strong", {}, title), h("p", {}, text))))),
        h("p", { class: "login-footnote" }, "Prosty dostęp do codziennej pracy szkoły.")),
      h("main", { class: "login-panel" }, h("div", { class: "login-card" }, h("p", { class: "eyebrow" }, "WITAJ W DZIENNIKU"), h("h1", {}, "Zaloguj się"), h("p", { class: "lead" }, "Otwórz swoją szkolną przestrzeń."), form,
        h("div", { class: "login-help" }, h("strong", {}, "Nie masz jeszcze konta?"), h("p", {}, "Poproś administratora swojej szkoły o dostęp.")),
        h("a", { class: "text-link", href: basePath + "swagger-ui/index.html" }, "Dokumentacja API", icon("arrow"))))));
  }

  function renderShell() {
    app.replaceChildren(h("div", { class: "app-shell" },
      h("aside", { class: "sidebar", id: "sidebar", "aria-label": "Nawigacja główna" },
        h("a", { class: "brand", href: "#/dashboard", "aria-label": "Dziennik — pulpit" }, h("span", { class: "brand-mark" }, icon("book")), h("span", {}, h("strong", { class: "brand-name" }, "Dziennik"), h("span", { class: "brand-subtitle" }, "Panel szkoły"))),
        h("nav", { id: "navigation" }),
        h("div", { class: "sidebar-footer" }, h("span", { class: "connection-dot" }), h("div", {}, h("strong", {}, "Twoje dane, Twój widok"), h("p", {}, "Dostęp zgodny z Twoją rolą.")), h("a", { href: basePath + "swagger-ui/index.html", title: "Otwórz dokumentację API", "aria-label": "Dokumentacja API", class: "icon-btn" }, icon("info")))),
      h("button", { type: "button", class: "sidebar-backdrop", "aria-label": "Zamknij nawigację", onclick: () => setMenu(false) }),
      h("div", { class: "workspace" }, h("header", { class: "topbar" },
        h("div", { class: "topbar-start" }, h("button", { class: "mobile-menu icon-btn", type: "button", "aria-controls": "sidebar", "aria-expanded": "false", "aria-label": "Otwórz nawigację", onclick: () => setMenu(!document.querySelector(".app-shell").classList.contains("is-open")) }, icon("menu")), h("div", { class: "breadcrumb", id: "breadcrumb" })),
        h("div", { class: "topbar-actions" }, h("time", { class: "topbar-date", datetime: new Date().toISOString().slice(0, 10) }, new Intl.DateTimeFormat("pl-PL", { day: "numeric", month: "long", year: "numeric" }).format(new Date())), h("div", { class: "account-pill" }, h("span", { class: "avatar", id: "account-avatar" }, initials()), h("div", {}, h("strong", { id: "account-name" }, `${state.actor.firstName} ${state.actor.lastName}`), h("small", { id: "account-role" }, roleLabel()))), iconButton("Wyloguj się", "logout", logout))), h("main", { class: "page", id: "main", tabindex: "-1" }))));
    renderNavigation();
    renderMain();
  }

  function setMenu(open) {
    const shell = document.querySelector(".app-shell");
    if (!shell) return;
    shell.classList.toggle("is-open", open);
    document.querySelector(".mobile-menu").setAttribute("aria-expanded", String(open));
    if (open) document.querySelector(".sidebar .nav-item")?.focus();
    else if (document.querySelector(".sidebar")?.contains(document.activeElement)) document.querySelector(".mobile-menu")?.focus();
  }
  function renderNavigation() {
    const nav = document.getElementById("navigation");
    if (!nav) return;
    nav.replaceChildren();
    const diaryViews = ["subjects", "enrollments", "grades"];
    if (state.actor.role !== "TEACHER") diaryViews.push("attendance");
    const groups = [["PRZEGLĄD", ["dashboard"]], ["ORGANIZACJA", ["schools", "academic-years", "classes", "users"]], ["DZIENNIK", diaryViews]];
    for (const [title, items] of groups) nav.append(h("div", { class: "nav-group" }, h("p", { class: "nav-label" }, title), items.map(key => {
      const definition = key === "attendance" ? { title: "Frekwencja", icon: "calendar" } : model.resources[key];
      return h("a", { href: `#/${key}`, class: `nav-item${state.view === key ? " is-active" : ""}`, "aria-current": state.view === key ? "page" : null, onclick: event => {
        if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        event.preventDefault();
        navigate(key);
      } }, icon(definition?.icon || "dashboard"), h("span", {}, definition?.title || "Pulpit"), model.resources[key] && h("span", { class: "nav-count" }, rows(key).length));
    })));
    const title = state.view === "attendance" ? "Frekwencja" : model.resources[state.view]?.title || "Pulpit";
    document.getElementById("breadcrumb").replaceChildren(h("span", {}, "Dziennik"), icon("chevron"), h("strong", {}, title));
  }

  async function refreshData(successMessage) {
    controller?.abort();
    controller = new AbortController();
    const { signal } = controller;
    const generation = state.generation;
    state.loading = true;
    state.error = null;
    renderMain();
    try {
      const actor = await api.request("/api/auth/me", { signal });
      const collections = await Promise.all(keys.map(key => api.all(key, { signal })));
      if (signal.aborted || generation !== state.generation) return;
      state.actor = actor;
      state.refs = Object.fromEntries(keys.map((key, index) => [key, collections[index]]));
      state.loading = false;
      if (state.view !== route().view) { state.query = ""; state.page = 0; state.sort = null; }
      state.view = route().view;
      document.getElementById("account-name").textContent = `${actor.firstName} ${actor.lastName}`;
      document.getElementById("account-role").textContent = roleLabel();
      document.getElementById("account-avatar").textContent = initials();
      renderNavigation();
      renderMain();
      if (successMessage) notify(successMessage);
      if (route().historyId) openHistory(route().historyId);
    } catch (failure) {
      if (signal.aborted || generation !== state.generation || !state.actor) return;
      state.loading = false;
      state.error = failure;
      renderMain();
    }
  }

  async function logout() {
    if (logoutPending) return;
    logoutPending = true;
    try {
      await api.logout();
      resetSession("Wylogowano Cię z dziennika.");
    } catch (failure) {
      if (state.actor) notify(failure.message);
    } finally { logoutPending = false; }
  }

  function pageHeader(title, subtitle, actions = []) {
    return h("div", { class: "page-heading" }, h("div", {}, h("p", { class: "page-kicker" }, model.isGlobalAdmin(state.actor) ? "WSZYSTKIE PLACÓWKI" : label("schools", state.actor.schoolId)), h("h1", { class: "page-title" }, title), h("p", { class: "page-subtitle" }, subtitle)), h("div", { class: "page-actions" }, actions));
  }
  function loading() {
    return h("div", { class: "loading-state", role: "status", "aria-label": "Wczytywanie danych", "aria-busy": "true" }, h("p", {}, "Wczytujemy Twój dziennik…"), h("div", { class: "stats-grid" }, [1, 2, 3, 4].map(() => h("div", { class: "skeleton" }))), h("div", { class: "skeleton skeleton-panel" }));
  }
  function renderMain() {
    attendance.cancelReport();
    const main = document.getElementById("main");
    if (!main || !state.actor) return;
    if (state.loading) { main.replaceChildren(loading()); return; }
    if (state.error) {
      main.replaceChildren(pageHeader("Nie udało się wczytać dziennika", "Odśwież widok, aby ponownie pobrać dane."), errorBox(state.error), button("Spróbuj ponownie", () => refreshData(), "primary", "refresh"));
      return;
    }
    if (state.view === "dashboard") renderDashboard(main);
    else if (state.view === "attendance") {
      if (state.actor.role === "TEACHER") main.replaceChildren(pageHeader("Frekwencja", "Otwórz swój przedmiot, aby sprawdzić listy obecności.", [button("Przejdź do przedmiotów", () => navigate("subjects"))]));
      else attendance.renderReport(main, state.actor);
    }
    else renderCollection(main);
  }

  function scoreBadge(score) { return h("span", { class: `score-badge score-${/^[A-F]$/.test(score) ? score : "A"}`, "aria-label": `Ocena ${score}` }, score); }
  function renderDashboard(main) {
    const teacher = state.actor.role === "TEACHER";
    const student = state.actor.role === "STUDENT";
    const metrics = student
      ? [["subjects", "Moje przedmioty"], ["enrollments", "Aktywne zapisy", rows("enrollments").filter(item => item.status === "ACTIVE").length], ["grades", "Moje oceny"], ["academic-years", "Lata szkolne"]]
      : teacher ? [["subjects", "Moje przedmioty"], ["users", "Uczniowie moich przedmiotów"], ["enrollments", "Zapisy uczniów"], ["grades", "Wystawione oceny"]]
      : [[model.isGlobalAdmin(state.actor) ? "schools" : "classes", model.isGlobalAdmin(state.actor) ? "Szkoły" : "Klasy"], ["users", "Użytkownicy"], ["subjects", "Przedmioty"], ["grades", "Oceny"]];
    const recent = [...rows("grades")].sort((a, b) => String(b.updatedAt || b.createdAt || b.dateOfScore).localeCompare(String(a.updatedAt || a.createdAt || a.dateOfScore))).slice(0, 5);
    const counts = Object.fromEntries(["A", "B", "C", "D", "E", "F"].map(score => [score, rows("grades").filter(grade => grade.score === score).length]));
    const maximum = Math.max(1, ...Object.values(counts));
    const quick = ["schools", "users", "academic-years", "classes", "subjects", "enrollments", "grades"].filter(key => model.canCreate(key, state.actor));
    main.replaceChildren(
      pageHeader(`Dzień dobry, ${state.actor.firstName}.`, "Oto przegląd Twojej szkolnej przestrzeni.", [button("Odśwież", () => refreshData(), "secondary", "refresh")]),
      h("div", { class: "stats-grid" }, metrics.map(([key, title, count]) => h("button", { class: "stat-card", type: "button", onclick: () => navigate(key) }, h("div", { class: "stat-top" }, h("span", { class: "stat-icon" }, icon(model.resources[key].icon)), icon("arrow")), h("strong", { class: "stat-value" }, count ?? rows(key).length), h("span", { class: "stat-label" }, title), h("span", { class: "stat-foot" }, "Zobacz listę")))),
      h("div", { class: "dashboard-grid" },
        h("div", { class: "dashboard-column" }, h("section", { class: "panel" }, h("div", { class: "panel-heading" }, h("div", {}, h("h2", { class: "panel-title" }, "Ostatnie oceny"), h("p", { class: "panel-subtitle" }, "Najnowsze wpisy i korekty w Twoim widoku.")), button("Wszystkie", () => navigate("grades"), "ghost", "arrow")),
          recent.length ? h("div", { class: "recent-list" }, recent.map(grade => h("button", { class: "recent-row", type: "button", onclick: () => showHistory(grade.id), "aria-label": `Historia oceny ${grade.score}, ${label("users", grade.studentId)}` }, scoreBadge(grade.score), h("span", { class: "recent-main" }, h("strong", {}, label("users", grade.studentId)), h("small", {}, label("subjects", grade.subjectId))), h("span", { class: "recent-meta" }, model.formatDate(grade.dateOfScore), icon("chevron"))))) : emptyState("Jeszcze bez ocen", student ? "Tutaj pojawią się oceny wystawione przez nauczyciela." : "Wystaw pierwszą ocenę uczniowi zapisanemu na przedmiot.", "grade")),
          h("section", { class: "panel role-panel" }, h("div", { class: "panel-heading" }, h("h2", { class: "panel-title" }, "Twój dostęp"), h("span", { class: "badge badge-success" }, roleLabel())), h("p", {}, student ? "Przeglądasz swoje przedmioty, zapisy i oceny. Zmiany wprowadza nauczyciel lub administrator." : teacher ? "Pracujesz ze swoimi przedmiotami. Możesz zapisywać uczniów, wystawiać oceny i przeglądać historię." : model.isGlobalAdmin(state.actor) ? "Zarządzasz placówkami, kontami oraz wszystkimi obszarami dziennika." : "Zarządzasz kontami, organizacją nauki i dziennikiem w swojej szkole."), h("div", { class: "role-account" }, icon("lock"), h("span", {}, state.actor.email)))),
        h("section", { class: "panel" }, h("div", { class: "panel-heading" }, h("div", {}, h("h2", { class: "panel-title" }, "Rozkład ocen"), h("p", { class: "panel-subtitle" }, `Liczba ocen: ${rows("grades").length} · skala A–F`))), h("div", { class: "grade-chart" }, Object.entries(counts).map(([score, count]) => {
          const bar = h("span", { class: `chart-bar score-${score}` });
          bar.style.width = `${Math.round(count / maximum * 100)}%`;
          return h("div", { class: "chart-row", "aria-label": `${score}: ${count} ocen` }, h("span", {}, score), h("div", { class: "chart-track" }, bar), h("strong", {}, count));
        })), h("p", { class: "helper chart-caption" }, "Dane obejmują tylko rekordy, do których masz dostęp."))),
      h("section", { class: "panel flow-panel" }, h("div", { class: "panel-heading" }, h("div", {}, h("h2", { class: "panel-title" }, "Od szkoły do oceny"), h("p", { class: "panel-subtitle" }, "Tak łączą się poszczególne części dziennika."))), h("div", { class: "flow-grid" }, ["schools", "academic-years", "classes", "subjects", "enrollments", "grades"].map((key, index) => h("button", { class: "flow-step", type: "button", onclick: () => navigate(key) }, h("span", { class: "flow-number" }, String(index + 1).padStart(2, "0")), icon(model.resources[key].icon), h("strong", { class: "flow-label" }, model.resources[key].title), h("small", {}, `Rekordy: ${rows(key).length}`), icon("chevron"))))),
      quick.length > 0 && h("section", { class: "quick-section" }, h("h2", { class: "section-label" }, "Szybkie działanie"), h("div", { class: "quick-actions" }, quick.map(key => h("button", { class: "action-card", type: "button", onclick: () => openEditor(key) }, icon(model.resources[key].icon), h("span", {}, model.resources[key].createLabel), icon("plus"))))));
  }

  function emptyState(title, message, name, action) {
    return h("div", { class: "empty-state" }, h("span", { class: "empty-icon" }, icon(name)), h("h3", {}, title), h("p", {}, message), action);
  }
  function renderCollection(main) {
    const key = state.view;
    const definition = model.resources[key];
    const create = model.canCreate(key, state.actor);
    const manageInvitations = key === "users" && state.actor.role === "ADMIN";
    const search = h("input", { class: "input search-input", type: "search", placeholder: `Szukaj na liście: ${definition.title.toLocaleLowerCase("pl")}`, "aria-label": `Szukaj: ${definition.title}`, value: state.query });
    const size = h("select", { class: "input page-size", "aria-label": "Liczba rekordów na stronie" }, [12, 24, 48].map(value => h("option", { value, selected: value === state.size }, `${value} na stronie`)));
    const results = h("div", { id: "collection-results" });
    search.addEventListener("input", () => { state.query = search.value; state.page = 0; renderResults(results, key); });
    size.addEventListener("change", () => { state.size = Number(size.value); state.page = 0; renderResults(results, key); });
    main.replaceChildren(pageHeader(definition.title, definition.description, [
      button("Odśwież", () => refreshData(), "secondary", "refresh"),
      manageInvitations && button("Zaproszenia", openInvitations),
      key === "enrollments" && state.actor.role === "TEACHER" && button("Zapisz ucznia po e-mailu", () => attendance.openEnrollment()),
      create && button(definition.createLabel, () => openEditor(key), manageInvitations ? "secondary" : "primary", "plus"),
      manageInvitations && button("Zaproś użytkownika", openInvitationForm, "primary", "plus")
    ]),
      h("section", { class: "panel collection-panel", "aria-label": definition.title }, h("div", { class: "table-toolbar" }, h("div", { class: "search-wrap" }, icon("search"), search), h("div", { class: "toolbar-end" }, h("span", { class: "badge badge-neutral" }, `Rekordy: ${rows(key).length}`), size)), results));
    renderResults(results, key);
  }
  function renderResults(container, key) {
    const definition = model.resources[key];
    const query = normalized(state.query.trim());
    const data = rows(key).filter(record => !query || definition.columns.some(column => normalized(model.formatValue(key, column.key, record[column.key], lookups())).includes(query)));
    const sortValue = record => /^(dateOf|startDate|endDate|joinedOn|leftOn)/.test(state.sort || "")
      ? record[state.sort] || "" : model.formatValue(key, state.sort, record[state.sort], lookups());
    if (state.sort) data.sort((a, b) => collator.compare(String(sortValue(a)), String(sortValue(b))) * state.direction);
    const pages = Math.max(1, Math.ceil(data.length / state.size));
    state.page = Math.min(state.page, pages - 1);
    const visible = data.slice(state.page * state.size, (state.page + 1) * state.size);
    if (!visible.length) {
      container.replaceChildren(emptyState(query ? "Brak pasujących wyników" : "Ta lista jest jeszcze pusta", query ? "Zmień wyszukiwaną frazę lub wyczyść pole wyszukiwania." : "Dostępne rekordy pojawią się tutaj po ich dodaniu.", definition.icon, !query && model.canCreate(key, state.actor) && button(definition.createLabel, () => openEditor(key), "primary", "plus")));
      return;
    }
    const head = h("tr", {}, definition.columns.map(column => h("th", { scope: "col", "aria-sort": state.sort === column.key ? state.direction === 1 ? "ascending" : "descending" : "none" }, h("button", { type: "button", class: "sort-button", onclick: () => { state.direction = state.sort === column.key ? -state.direction : 1; state.sort = column.key; renderResults(container, key); } }, column.label, state.sort === column.key ? state.direction === 1 ? " ↑" : " ↓" : ""))), h("th", { scope: "col", class: "actions-column" }, "Działania"));
    const body = visible.map(record => {
      const canManageAttendance = key === "subjects" && model.canEdit("enrollments", state.actor, { subjectId: record.id }, lookups());
      const editable = model.canEdit(key, state.actor, record, lookups());
      const cells = definition.columns.map((column, index) => {
        const value = record[column.key];
        const text = model.formatValue(key, column.key, value, lookups());
        let child = text;
        if (column.key === "score") child = scoreBadge(value);
        if (["status", "enabled", "role", "type"].includes(column.key)) child = h("span", { class: `badge ${value === "ACTIVE" || value === true ? "badge-success" : value === "WITHDRAWN" || value === false ? "badge-warning" : "badge-neutral"}` }, text);
        return h("td", {}, index === 0 ? h("span", { class: "cell-main" }, child) : child);
      });
      const actions = h("div", { class: "row-actions" },
        key === "grades" && iconButton("Historia oceny", "history", () => showHistory(record.id)),
        canManageAttendance && button("Frekwencja", () => attendance.openLessons(record)),
        editable && iconButton(key === "enrollments" ? "Zmień status zapisu" : "Edytuj rekord", "edit", () => openEditor(key, record)),
        model.canDelete(key, state.actor, record, lookups()) && iconButton("Usuń rekord", "trash", () => confirmDelete(key, record)),
        !canManageAttendance && !editable && key !== "grades" && h("span", { class: "cell-muted" }, "Tylko odczyt"));
      return h("tr", {}, cells, h("td", {}, actions));
    });
    const previous = button("Poprzednia", () => { state.page--; renderResults(container, key); }, "secondary");
    const next = button("Następna", () => { state.page++; renderResults(container, key); }, "secondary");
    previous.disabled = state.page === 0;
    next.disabled = state.page + 1 >= pages;
    container.replaceChildren(h("div", { class: "table-wrap" }, h("table", { class: "data-table" }, h("caption", { class: "sr-only" }, definition.title), h("thead", {}, head), h("tbody", {}, body))), h("div", { class: "pagination" }, h("span", { role: "status" }, `Wyniki: ${state.page * state.size + 1}–${Math.min((state.page + 1) * state.size, data.length)} / ${data.length}`), h("div", { class: "pagination-actions" }, previous, h("span", {}, `${state.page + 1} / ${pages}`), next)));
  }

  function closeModal(force = false) {
    if (mutationPending && force !== true) return;
    modalController?.abort();
    modalController = null;
    if (modal.open) modal.close();
  }
  function dismissModal() {
    if (mutationPending) return;
    if (modalKind === "history" && route().historyId === modal.dataset.historyId) history.replaceState(null, "", "#/grades");
    closeModal();
  }
  modal.addEventListener("cancel", event => {
    event.preventDefault();
    dismissModal();
  });
  function setModalBusy(busy) {
    mutationPending = busy;
    modal.setAttribute("aria-busy", String(busy));
    for (const control of modal.querySelectorAll("button")) control.disabled = busy;
    for (const control of modal.querySelectorAll("input, select")) {
      if (busy && !control.disabled) { control.dataset.disabledForSave = "true"; control.disabled = true; }
      else if (!busy && control.dataset.disabledForSave) { control.disabled = false; delete control.dataset.disabledForSave; }
    }
  }
  modal.addEventListener("close", () => {
    // A close event can be queued while another dialog is already being opened.
    if (modal.open) return;
    modalController?.abort();
    modalController = null;
    modalKind = null;
    modal.replaceChildren();
    if (lastFocus?.isConnected) lastFocus.focus();
  });
  function beginModal(title, subtitle, kind) {
    closeModal();
    lastFocus = document.activeElement;
    modalKind = kind;
    modalController = new AbortController();
    const body = h("div", { class: "dialog-body" });
    modal.replaceChildren(h("div", { class: "dialog-header" }, h("div", {}, h("p", { class: "eyebrow" }, "DZIENNIK"), h("h2", { id: "modal-title" }, title), subtitle && h("p", { class: "helper" }, subtitle)), iconButton("Zamknij okno", "close", dismissModal)), body);
    modal.showModal();
    return { body, signal: modalController.signal };
  }

  function invitationField(label, control, help) {
    const helpId = `${control.id}-help`;
    if (help) control.setAttribute("aria-describedby", helpId);
    return h("div", { class: "field" },
      h("label", { class: "field-label", for: control.id }, label),
      control,
      help && h("p", { id: helpId, class: "helper" }, help)
    );
  }

  function openInvitationForm() {
    if (state.actor?.role !== "ADMIN") return;
    const { body, signal } = beginModal("Zaproś użytkownika", "Utwórz jednorazowy link ważny przez 24 godziny.", "invite");
    const email = h("input", { id: "invite-email", class: "input", type: "email", required: true, maxlength: 50, autocomplete: "off" });
    const role = h("select", { id: "invite-role", class: "input", required: true },
      h("option", { value: "" }, "Wybierz rolę…"),
      ["STUDENT", "TEACHER"].map(value => h("option", { value }, model.enumLabels[value]))
    );
    const school = model.isGlobalAdmin(state.actor) ? h("select", { id: "invite-school", class: "input", required: true },
      h("option", { value: "" }, "Wybierz szkołę…"),
      model.optionRows(rows("schools"), "schools", lookups()).map(option => h("option", { value: option.value }, option.label))
    ) : null;
    const errors = h("div", {});
    const submit = h("button", { type: "submit", class: "btn btn-primary" }, "Utwórz zaproszenie");
    const form = h("form", { "aria-label": "Utwórz zaproszenie" },
      errors,
      invitationField("E-mail", email),
      invitationField("Rola", role),
      school ? invitationField("Szkoła", school) : h("p", { class: "helper" }, `Szkoła · ${label("schools", state.actor.schoolId)}`),
      h("div", { class: "dialog-footer" }, button("Anuluj", closeModal), submit)
    );
    form.addEventListener("submit", async event => {
      event.preventDefault();
      if (submit.disabled || !form.reportValidity()) return;
      const payload = { email: email.value.trim(), role: role.value };
      if (school) payload.schoolId = school.value;
      setModalBusy(true);
      errors.replaceChildren();
      try {
        const result = await api.request("/api/invitations", { method: "POST", body: payload, signal });
        if (signal.aborted) return;
        if (typeof result.token !== "string" || !invitationToken.test(result.token)) {
          throw new Error("Nie udało się odczytać linku. Sprawdź listę zaproszeń.");
        }
        showInvitationLink(body, result, signal);
      } catch (failure) {
        if (!signal.aborted) errors.replaceChildren(errorBox(failure));
      } finally {
        setModalBusy(false);
      }
    });
    body.append(form);
    email.focus();
  }

  function showInvitationLink(body, result, signal) {
    const url = new URL(basePath, location.origin);
    url.hash = `/invite/${result.token}`;
    const link = h("input", { id: "invite-link", class: "input", value: url.href, readOnly: true, autocomplete: "off" });
    const feedback = h("p", { class: "helper", role: "status" });
    const copy = button("Skopiuj link", async () => {
      try {
        await navigator.clipboard.writeText(link.value);
        if (!signal.aborted) feedback.textContent = "Skopiowano link zaproszenia.";
      } catch {
        if (signal.aborted) return;
        link.focus();
        link.select();
        feedback.textContent = "Link został zaznaczony. Skopiuj go skrótem klawiaturowym lub z menu przeglądarki.";
      }
    }, "primary");
    body.replaceChildren(
      h("p", { class: "notice" }, `Zaproszenie dla ${result.invitation.email} jest ważne do ${invitationExpiry.format(new Date(result.invitation.expiresAt))}. Skopiuj link teraz i przekaż go tej osobie.`),
      invitationField("Link zaproszenia", link),
      feedback,
      h("div", { class: "dialog-footer" }, button("Zaproszenia", openInvitations), copy)
    );
    link.focus();
  }

  function openInvitations() {
    if (state.actor?.role !== "ADMIN") return;
    const { body, signal } = beginModal("Zaproszenia", "Sprawdź stan zaproszeń i unieważnij nieużywane linki.", "invitations");

    async function load(pageNumber, message = "") {
      body.replaceChildren(h("p", { role: "status" }, "Wczytywanie zaproszeń…"));
      try {
        const page = await api.page("invitations", pageNumber, 10, { signal });
        if (signal.aborted) return;
        const errors = h("div", {});
        const items = h("ul", { class: "invitation-list", "aria-label": "Lista zaproszeń" });
        for (const invitation of page.items) {
          const pending = invitation.status === "PENDING";
          const expiresAt = invitationExpiry.format(new Date(invitation.expiresAt));
          const revoke = pending && button("Unieważnij", async () => {
            if (mutationPending) return;
            setModalBusy(true);
            errors.replaceChildren();
            try {
              await api.request(`/api/invitations/${encodeURIComponent(invitation.id)}/revoke`, { method: "POST", signal });
            } catch (failure) {
              if (!signal.aborted) errors.replaceChildren(errorBox(failure));
              return;
            } finally {
              setModalBusy(false);
            }
            if (!signal.aborted) await load(pageNumber, "Zaproszenie unieważniono.");
          });
          items.append(h("li", { class: "invitation-item" },
            h("div", { class: "invitation-details" },
              h("strong", {}, invitation.email),
              h("p", { class: "helper" }, `${model.enumLabels[invitation.role]} · ${label("schools", invitation.schoolId)}`),
              h("p", { class: "helper" }, "Ważne do ", h("time", { datetime: invitation.expiresAt }, expiresAt))
            ),
            h("div", { class: "invitation-actions" },
              h("span", { class: `badge ${pending ? "badge-success" : "badge-neutral"}` }, invitationStatuses.get(invitation.status) || invitation.status),
              revoke
            )
          ));
        }
        body.replaceChildren(
          message && h("p", { class: "notice", role: "status" }, message),
          errors,
          page.items.length ? items : h("p", { class: "helper" }, "Brak zaproszeń."),
          h("div", { class: "pagination" }, h("span", {}, `Zaproszenia: ${page.totalElements}`),
            h("div", { class: "pagination-actions" },
              pageNumber > 0 && button("Poprzednia", () => load(pageNumber - 1)),
              pageNumber + 1 < page.totalPages && button("Następna", () => load(pageNumber + 1)),
              button("Odśwież", () => load(pageNumber))
            )
          )
        );
      } catch (failure) {
        if (!signal.aborted) body.replaceChildren(errorBox(failure), button("Spróbuj ponownie", () => load(pageNumber)));
      }
    }

    load(0);
  }

  function renderActivation(token) {
    const replacementHelp = "Ten link jest nieprawidłowy, wygasł albo został już wykorzystany lub unieważniony. Poproś administratora swojej szkoły o nowe zaproszenie.";
    const returnToLogin = h("a", { class: "text-link", href: "#/login" }, "Przejdź do logowania", icon("arrow"));
    const card = h("section", { class: "panel login-card", "aria-labelledby": "activation-title" },
      h("p", { class: "eyebrow" }, "DZIENNIK SZKOLNY"),
      h("h1", { id: "activation-title" }, "Aktywuj konto")
    );
    app.replaceChildren(h("main", { class: "activation-page" }, card));
    if (!token) {
      card.append(h("p", { class: "form-error", role: "alert" }, replacementHelp), returnToLogin);
      return;
    }

    const firstName = h("input", { id: "activate-first-name", class: "input", required: true, maxlength: 50, autocomplete: "given-name" });
    const lastName = h("input", { id: "activate-last-name", class: "input", required: true, maxlength: 50, autocomplete: "family-name" });
    const dateOfBirth = h("input", { id: "activate-birth-date", class: "input", type: "date", required: true, max: model.localDate(-1), autocomplete: "bday" });
    const password = h("input", { id: "activate-password", class: "input", type: "password", required: true, minlength: 12, maxlength: 128, autocomplete: "new-password" });
    const errors = h("div", {});
    const submit = h("button", { class: "btn btn-primary", type: "submit" }, "Aktywuj konto", icon("arrow"));
    const fields = h("fieldset", { class: "activation-fields" },
      invitationField("Imię", firstName),
      invitationField("Nazwisko", lastName),
      invitationField("Data urodzenia", dateOfBirth),
      invitationField("Hasło", password, "Od 12 do 128 znaków."),
      submit
    );
    const form = h("form", { "aria-label": "Aktywacja konta" }, errors, fields);
    form.addEventListener("submit", async event => {
      event.preventDefault();
      if (fields.disabled || !form.reportValidity()) return;
      fields.disabled = true;
      form.setAttribute("aria-busy", "true");
      errors.replaceChildren();
      try {
        await api.request("/api/invitations/accept", { method: "POST", body: {
          token, firstName: firstName.value.trim(), lastName: lastName.value.trim(),
          dateOfBirth: dateOfBirth.value, password: password.value
        } });
        if (!form.isConnected || route().token !== token) return;
        history.replaceState(null, "", "#/login");
        state.view = "login";
        renderLogin(false, "Konto zostało aktywowane. Zaloguj się adresem e-mail z zaproszenia.");
      } catch (failure) {
        if (!form.isConnected) return;
        if (failure.status === 404 || failure.status === 410) {
          fields.remove();
          errors.replaceChildren(h("p", { class: "form-error", role: "alert" }, replacementHelp));
        } else if (failure.status === 409) {
          fields.remove();
          errors.replaceChildren(h("p", { class: "form-error", role: "alert" }, "Nie można aktywować konta dla tego adresu. Jeśli masz już konto, zaloguj się. W razie trudności skontaktuj się z administratorem swojej szkoły."));
        } else {
          errors.replaceChildren(errorBox(failure));
        }
      } finally {
        password.value = "";
        fields.disabled = false;
        form.removeAttribute("aria-busy");
      }
    });
    card.append(h("p", { class: "lead" }, "Uzupełnij dane i ustaw hasło. Do logowania użyjesz adresu e-mail, na który otrzymano zaproszenie."), form, returnToLogin);
  }

  function showHistory(id) {
    closeModal();
    location.hash = `#/grades/history/${encodeURIComponent(id)}`;
  }
  async function openHistory(id) {
    if (modal.open && modalKind === "history" && modal.dataset.historyId === id) return;
    const { body, signal } = beginModal("Historia oceny", "Każda korekta pozostaje w dzienniku, także po usunięciu oceny.", "history");
    modal.dataset.historyId = id;
    body.append(h("p", { role: "status" }, "Wczytywanie historii…"));
    try {
      const revisions = await api.request(`/api/grades/${encodeURIComponent(id)}/history`, { signal });
      if (signal.aborted) return;
      const first = revisions[0];
      body.replaceChildren(first && h("div", { class: "notice" }, h("strong", {}, label("users", first.studentId)), " · ", label("subjects", first.subjectId)),
        h("ol", { class: "history-list" }, revisions.map(revision => h("li", { class: "history-item" }, h("span", { class: `history-marker ${revision.action === "DELETED" ? "is-deleted" : ""}` }, icon(revision.action === "DELETED" ? "trash" : "history")), h("div", { class: "history-detail" }, h("div", { class: "history-heading" }, h("strong", {}, model.enumLabels[revision.action] || revision.action), scoreBadge(revision.score)), h("p", {}, label("users", revision.changedById)), h("small", {}, new Intl.DateTimeFormat("pl-PL", { dateStyle: "medium", timeStyle: "short" }).format(new Date(revision.changedAt)), ` · data oceny: ${model.formatDate(revision.dateOfScore)}`))))), h("p", { class: "helper" }, "Adres tego widoku możesz zachować — historia jest dostępna również po usunięciu oceny."));
    } catch (failure) { if (!signal.aborted && state.actor) body.replaceChildren(errorBox(failure)); }
  }

  async function openEditor(key, item = null) {
    const definition = model.resources[key];
    if (!state.actor || state.loading) return;
    const { body, signal } = beginModal(item ? key === "enrollments" ? "Zmień status zapisu" : `Edytuj: ${definition.singular.toLocaleLowerCase("pl")}` : definition.createLabel, "Uzupełnij dane. Pola oznaczone gwiazdką są wymagane.", "editor");
    let record = item;
    if (item) {
      body.append(h("p", { role: "status" }, "Pobieranie aktualnych danych…"));
      try { record = await api.request(`${definition.endpoint}/${encodeURIComponent(item.id)}`, { signal }); }
      catch (failure) { if (!signal.aborted && state.actor) body.replaceChildren(errorBox(failure)); return; }
    }
    if (signal.aborted) return;
    const values = {};
    const form = h("form", { "aria-label": item ? "Edycja rekordu" : definition.createLabel });
    const errors = h("div", { class: "editor-errors" });
    const grid = h("div", { class: "form-grid" });
    const save = h("button", { class: "btn btn-primary", type: "submit" }, icon("check"), record ? "Zapisz zmiany" : "Dodaj rekord");
    const controls = new Map();

    function renderFields(focusName) {
      grid.replaceChildren();
      controls.clear();
      // A sole valid required relation is selected without inventing a record.
      let fields;
      for (let pass = 0; pass < 3; pass++) {
        fields = model.formFields(key, record, state.actor, lookups(), values);
        let changed = false;
        for (const field of fields) {
          let value = field.type === "password" && Object.hasOwn(values, field.name) ? values[field.name] : field.value;
          if (field.type === "select" && field.required && !value && field.options.length === 1) value = field.options[0].value;
          if (!Object.hasOwn(values, field.name) || values[field.name] !== value) { values[field.name] = value; changed = true; }
        }
        if (!changed) break;
      }
      for (const field of fields) {
        const id = "editor-" + field.name;
        const hint = field.help || (field.type === "select" && !field.options.length ? "Najpierw dodaj lub wybierz wymagane powiązane dane." : "");
        const attributes = { id, name: field.name, class: "input", required: field.required, disabled: field.disabled, readOnly: field.readOnly, min: field.min, max: field.max, minlength: field.minLength, maxlength: field.maxLength, autocomplete: field.autocomplete || (field.type === "password" ? "new-password" : "off"), placeholder: field.placeholder, "aria-describedby": hint ? id + "-help" : null };
        const input = field.type === "select"
          ? h("select", attributes, h("option", { value: "" }, field.required ? "Wybierz…" : field.name === "schoolId" ? "Bez szkoły — administrator globalny" : "Nie przypisano"), field.options.map(option => h("option", { value: option.value }, option.label)))
          : h("input", { ...attributes, type: field.type });
        if (field.type === "checkbox") input.checked = Boolean(values[field.name]);
        else input.value = values[field.name] ?? "";
        controls.set(field.name, input);
        input.addEventListener("input", () => { values[field.name] = field.type === "checkbox" ? input.checked : input.value; });
        if (["schoolId", "academicYearId", "subjectId", "role", "status", "startDate"].includes(field.name)) input.addEventListener("change", () => {
          for (const [name, control] of controls) values[name] = control.type === "checkbox" ? control.checked : control.value;
          if (field.name === "schoolId" && key !== "users") for (const dependent of ["academicYearId", "classId", "teacherId", "studentId"]) values[dependent] = "";
          if (field.name === "academicYearId") values.classId = "";
          if (field.name === "subjectId" && !record) values.studentId = "";
          renderFields(field.name);
        });
        grid.append(h("div", { class: `field${field.type === "checkbox" ? " form-full checkbox-field" : ""}` }, field.type === "checkbox" ? h("label", { class: "checkbox-label", for: id }, input, field.label) : [h("label", { class: "field-label", for: id }, field.label, field.required && h("span", { "aria-hidden": "true" }, " *")), input], hint && h("p", { id: id + "-help", class: "helper" }, hint)));
      }
      if (focusName) controls.get(focusName)?.focus();
    }
    renderFields();
    form.append(errors, grid, key === "enrollments" && !record && state.actor.role === "TEACHER" && h("p", { class: "notice" }, "Jeśli ucznia nie ma na liście, wybierz „Zapisz ucznia po e-mailu” w panelu zapisów."), h("div", { class: "dialog-footer" }, button("Anuluj", closeModal, "secondary"), save));
    form.addEventListener("submit", async event => {
      event.preventDefault();
      if (!form.reportValidity() || save.disabled) return;
      for (const [name, control] of controls) values[name] = control.type === "checkbox" ? control.checked : control.value;
      save.disabled = true;
      errors.replaceChildren();
      try {
        const payload = model.buildPayload(key, values, record);
        setModalBusy(true);
        await api.request(record ? `${definition.endpoint}/${encodeURIComponent(record.id)}` : definition.endpoint, { method: record ? "PUT" : "POST", body: payload, signal });
        setModalBusy(false);
        closeModal();
        await refreshData(record ? "Zapisano zmiany." : "Dodano nowy rekord.");
      } catch (failure) {
        if (!signal.aborted && state.actor) { errors.replaceChildren(errorBox(failure)); errors.scrollIntoView({ block: "nearest" }); }
      } finally { setModalBusy(false); save.disabled = false; }
    });
    body.replaceChildren(form);
    requestAnimationFrame(() => { if (!signal.aborted) body.querySelector("input:not([disabled]), select:not([disabled])")?.focus(); });
  }

  function confirmDelete(key, record) {
    const { body, signal } = beginModal("Usunąć rekord?", "Tej operacji nie można cofnąć w panelu.", "delete");
    const definition = model.resources[key];
    const description = record.name || (key === "users" ? `${record.firstName} ${record.lastName}` : `Ocena ${record.score} · ${label("users", record.studentId)}`);
    const errors = h("div", {});
    const remove = button("Usuń rekord", async () => {
      remove.disabled = true;
      setModalBusy(true);
      try {
        await api.request(`${definition.endpoint}/${encodeURIComponent(record.id)}`, { method: "DELETE", signal });
        setModalBusy(false);
        closeModal();
        await refreshData("Usunięto rekord.");
        if (key === "grades" && state.actor) showHistory(record.id);
      } catch (failure) { if (!signal.aborted && state.actor) errors.replaceChildren(errorBox(failure)); }
      finally { setModalBusy(false); remove.disabled = false; }
    }, "danger", "trash");
    body.append(errors, h("p", { class: "confirm-copy" }, "Wybrany rekord: ", h("strong", {}, description)), h("p", { class: "helper" }, key === "grades" ? "Historia oceny zostanie zachowana i otwarta po usunięciu." : "Rekordu z powiązaniami nie będzie można usunąć. Możesz otrzymać informację o konflikcie."), h("div", { class: "dialog-footer" }, button("Zachowaj rekord", closeModal, "secondary"), remove));
  }

  document.addEventListener("keydown", event => { if (event.key === "Escape") setMenu(false); });
  async function start() {
    const next = route();
    if (showPublicRoute(next)) return;
    state.view = next.view;
    const generation = state.generation;
    renderLogin(true);
    try {
      const actor = await api.me();
      if (generation !== state.generation || showPublicRoute(route())) return;
      state.actor = actor;
      state.generation++;
      state.view = route().view;
      renderShell();
      await refreshData();
    } catch (failure) {
      if (generation !== state.generation || showPublicRoute(route())) return;
      if (failure.status === 401) renderLogin();
      else renderLogin(false, failure.message);
    }
  }
  start();
})();
