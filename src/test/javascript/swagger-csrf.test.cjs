const assert = require("node:assert/strict");
const { test } = require("node:test");
const { createCsrfInterceptor } = require("../../main/resources/static/js/swagger-csrf.js");

const page = "http://localhost:8080/swagger-ui/index.html";
const csrf = (token) => ({ ok: true, json: async () => ({ token, headerName: "X-CSRF-TOKEN" }) });

test("fetches a fresh token for login and the next write after token rotation", async () => {
  const calls = [];
  let token = "before-login";
  const intercept = createCsrfInterceptor(async (url, options) => {
    calls.push({ url, options });
    return csrf(token);
  }, page);
  const login = await intercept({ url: "/api/auth/login", method: "POST", headers: {} });
  assert.equal(login.headers["X-CSRF-TOKEN"], "before-login");
  token = "after-login";
  const create = await intercept({ url: "/api/schools", method: "POST", headers: { "X-CSRF-TOKEN": "stale" } });
  assert.equal(create.headers["X-CSRF-TOKEN"], "after-login");
  assert.equal(create.credentials, "same-origin");
  assert.equal(calls.length, 2);
  assert.equal(calls[0].url, "http://localhost:8080/api/auth/csrf");
  assert.equal(calls[0].options.credentials, "same-origin");
  assert.equal(calls[0].options.cache, "no-store");
  assert.equal(calls[0].options.redirect, "error");
});

test("uses the application context path and handles all unsafe methods", async () => {
  const urls = [];
  const intercept = createCsrfInterceptor(async (url) => {
    urls.push(url);
    return csrf("current");
  }, "https://school.test/diary/swagger-ui/index.html");
  for (const method of ["POST", "PUT", "PATCH", "DELETE"]) {
    const request = await intercept({ url: "/diary/api/grades", method });
    assert.equal(request.headers["X-CSRF-TOKEN"], "current");
  }
  assert.deepEqual(urls, Array(4).fill("https://school.test/diary/api/auth/csrf"));
});

test("does not fetch tokens for safe requests", async () => {
  const intercept = createCsrfInterceptor(() => assert.fail("unexpected token fetch"), page);
  for (const method of ["GET", "HEAD", "OPTIONS", "TRACE"]) {
    const request = await intercept({ url: "/api/auth/me", method });
    assert.deepEqual(request.headers, {});
  }
});

test("never forwards CSRF tokens to a different host, port or protocol", async () => {
  const intercept = createCsrfInterceptor(() => assert.fail("unexpected token fetch"), page);
  for (const url of ["https://other.test/api/schools", "http://localhost:9090/api/schools", "https://localhost:8080/api/schools"]) {
    const request = await intercept({ url, method: "POST", headers: { "x-CsRf-ToKeN": "secret", Accept: "application/json" } });
    assert.deepEqual(request.headers, { Accept: "application/json" });
    assert.equal(request.credentials, undefined);
  }
});

test("fails closed when obtaining a token fails", async () => {
  const intercept = createCsrfInterceptor(async () => ({ ok: false }), page);
  await assert.rejects(intercept({ url: "/api/schools", method: "POST" }), /Could not obtain a CSRF token/);
});

test("rejects malformed token responses and unexpected header names", async () => {
  for (const body of [{ token: "", headerName: "X-CSRF-TOKEN" }, { token: "secret", headerName: "Authorization" }, {}]) {
    const intercept = createCsrfInterceptor(async () => ({ ok: true, json: async () => body }), page);
    await assert.rejects(intercept({ url: "/api/schools", method: "POST" }), /Invalid CSRF response/);
  }
});
