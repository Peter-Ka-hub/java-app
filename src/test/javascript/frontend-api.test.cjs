const assert = require("node:assert/strict");
const { test } = require("node:test");
const fs = require("node:fs");
const vm = require("node:vm");
const { createApi, ApiError } = require("../../main/resources/static/ui/api.js");

const json = (body, status = 200, headers = {}) => new Response(JSON.stringify(body), {
  status,
  headers: { "Content-Type": "application/json", ...headers }
});
const csrf = (token) => json({ token, headerName: "X-CSRF-TOKEN" });

test("login and writes obtain fresh CSRF state, and logout accepts 204 without caching secrets", async () => {
  const calls = [];
  let token = "before-login";
  const api = createApi({ fetchImpl: async (url, init) => {
    calls.push({ url, init });
    if (url.endsWith("/csrf")) return csrf(token);
    if (url.endsWith("/login")) {
      assert.equal(init.headers["X-CSRF-TOKEN"], "before-login");
      assert.deepEqual(JSON.parse(init.body), { email: "admin@test.local", password: "test-password" });
      token = "after-login";
      return json({ id: "admin", role: "ADMIN" });
    }
    assert.equal(init.headers["X-CSRF-TOKEN"], "after-login");
    return url.endsWith("/logout") ? new Response(null, { status: 204 }) : json({ id: "school" }, 201);
  }});
  assert.equal((await api.login("admin@test.local", "test-password")).id, "admin");
  assert.equal((await api.request("/api/schools", { method: "POST", body: { name: "School" } })).id, "school");
  assert.equal(await api.logout(), null);
  assert.equal(calls.filter(({ url }) => url.endsWith("/csrf")).length, 3);
  assert.ok(calls.every(({ init }) => init.credentials === "same-origin" && init.cache === "no-store" && init.redirect === "error"));
  assert.deepEqual(Object.keys(api).sort(), ["all", "login", "logout", "me", "page", "request"]);
});

test("a revoked session emits one 401 notification and a Polish sign-in message", async () => {
  const notifications = [];
  const api = createApi({
    fetchImpl: async () => json({ message: "Credentials changed; sign in again" }, 401),
    onUnauthorized: (error) => notifications.push(error)
  });
  await assert.rejects(api.me(), (error) => {
    assert.ok(error instanceof ApiError);
    assert.equal(error.status, 401);
    assert.match(error.message, /Zaloguj się ponownie/);
    assert.equal(error.serverMessage, "Credentials changed; sign in again");
    return true;
  });
  assert.equal(notifications.length, 1);
});

test("a conflict never retries a mutation", async () => {
  let writes = 0;
  const api = createApi({ fetchImpl: async (url) => {
    if (url.endsWith("/csrf")) return csrf("valid");
    writes++;
    return json({ message: "Resource changed. Reload it and retry." }, 409);
  }});
  await assert.rejects(api.request("/api/grades/grade-id", {
    method: "PUT", body: { score: "A", version: 0 }
  }), (error) => error.status === 409 && /Odśwież/.test(error.message));
  assert.equal(writes, 1);
});

test("validation details and Retry-After reach the form without losing server context", async () => {
  const api = createApi({ fetchImpl: async () => json({
    message: "Validation failed", errors: ["name: must not be blank", "startDate: invalid"]
  }, 400) });
  await assert.rejects(api.request("/api/schools"), (error) => {
    assert.equal(error.status, 400);
    assert.deepEqual(error.errors, ["name: must not be blank", "startDate: invalid"]);
    assert.equal(error.serverMessage, "Validation failed");
    return true;
  });
  const limited = createApi({ fetchImpl: async () => json({ message: "Too many attempts" }, 429, { "Retry-After": "30" }) });
  await assert.rejects(limited.me(), (error) => error.status === 429 && error.retryAfter === 30 && /30 s/.test(error.message));
});

test("all reads every server page, including more than one hundred records", async () => {
  const pages = [];
  const api = createApi({ fetchImpl: async (url) => {
    const target = new URL(url);
    assert.equal(target.pathname, "/api/users");
    assert.equal(target.searchParams.get("size"), "100");
    const page = Number(target.searchParams.get("page"));
    pages.push(page);
    const count = page < 2 ? 100 : 5;
    return json({ items: Array.from({ length: count }, (_, index) => ({ id: String(page * 100 + index) })), totalPages: 3 });
  }});
  const items = await api.all("users");
  assert.equal(items.length, 205);
  assert.equal(items.at(-1).id, "204");
  assert.deepEqual(pages, [0, 1, 2]);
});

test("pagination is bounded by the reported page count and abort stops the next request", async () => {
  const controller = new AbortController();
  let calls = 0;
  const api = createApi({ fetchImpl: async () => {
    calls++;
    controller.abort();
    return json({ items: [{ id: "first" }], totalPages: 3 });
  }});
  await assert.rejects(api.all("users", { signal: controller.signal }), (error) => error.name === "AbortError");
  assert.equal(calls, 1);
  const empty = createApi({ fetchImpl: async () => json({ items: [], totalPages: 0 }) });
  assert.deepEqual(await empty.all("schools"), []);
});

test("foreign or non-API URLs are rejected before credentials or a token are sent", async () => {
  let requests = 0;
  const api = createApi({ fetchImpl: async () => { requests++; throw new Error("Unexpected fetch"); } });
  for (const path of ["https://other.test/api/users", "//other.test/api/users", "http://localhost:9090/api/users", "/api/../../outside", "http://user:password@localhost/api/users", "/swagger-ui/index.html"]) {
    await assert.rejects(api.request(path, { method: "POST", body: { password: "secret" } }), TypeError);
  }
  assert.equal(requests, 0);
});

test("the client honors an application context path", async () => {
  const urls = [];
  const api = createApi({ baseUrl: "https://school.test/diary/", fetchImpl: async (url) => {
    urls.push(url);
    return url.endsWith("/csrf") ? csrf("current") : json({ id: "school" });
  }});
  await api.request("/api/schools", { method: "POST", body: { name: "School" } });
  assert.deepEqual(urls, ["https://school.test/diary/api/auth/csrf", "https://school.test/diary/api/schools"]);
});

test("a failed or malformed CSRF response prevents the write", async () => {
  for (const response of [json({ message: "unavailable" }, 503), json({ token: "secret", headerName: "Authorization" }), json({ token: "", headerName: "X-CSRF-TOKEN" })]) {
    let calls = 0;
    const api = createApi({ fetchImpl: async () => { calls++; return response; } });
    await assert.rejects(api.logout(), ApiError);
    assert.equal(calls, 1);
  }
});

test("the browser export does not read or write persistent browser storage", async () => {
  const sandbox = {
    URL,
    location: { href: "http://localhost/ui/index.html" },
    fetch: async (url) => url.endsWith("/csrf") ? csrf("valid") : json({ id: "student", role: "STUDENT" })
  };
  for (const storage of ["localStorage", "sessionStorage"]) Object.defineProperty(sandbox, storage, {
    get() { assert.fail("Credentials must not be persisted in " + storage); }
  });
  vm.runInNewContext(fs.readFileSync(require.resolve("../../main/resources/static/ui/api.js"), "utf8"), sandbox);
  const api = sandbox.DiaryApi.createApi();
  assert.equal((await api.login("student@test.local", "never-store-this-password")).id, "student");
  assert.equal(sandbox.password, undefined);
  assert.equal(sandbox.token, undefined);
});
