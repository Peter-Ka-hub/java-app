const { test } = require("node:test");
const assert = require("node:assert/strict");
const path = require("node:path");
const { chromium } = require("playwright");

test("Back cancels pending grade history and Forward opens it again", { timeout: 30000 }, async t => {
  const browser = await chromium.launch();
  t.after(() => browser.close());
  const page = await browser.newPage();
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));

  const origin = "https://school-diary.test";
  const staticDir = path.resolve(__dirname, "../../main/resources/static");
  const assets = ["/favicon.svg", "/ui/app.js", "/ui/model.js", "/ui/api.js", "/ui/attendance.js", "/ui/styles.css"];
  const id = "5d2d609b-81ac-404e-b0d8-fae86f377841";
  const actor = { id: "student", role: "STUDENT", firstName: "Ada", lastName: "Nowak", schoolId: "school" };
  const grade = { id, studentId: actor.id, subjectId: "math", score: "B", dateOfScore: "2026-01-02" };
  const revision = { ...grade, action: "CREATED", changedById: "teacher", changedAt: "2026-01-02T12:00:00Z" };
  const firstHistoryRequest = Promise.withResolvers();
  let historyRequests = 0;
  await page.addInitScript(() => {
    const fetch = window.fetch.bind(window);
    window.fetch = (url, init) => {
      if (new URL(url, location.href).pathname.endsWith("/history")) window.lastHistorySignal = init.signal;
      return fetch(url, init);
    };
  });
  await page.route("**/*", route => {
    const url = new URL(route.request().url());
    if (url.origin !== origin) return route.abort();
    if (url.pathname === "/") return route.fulfill({ path: path.join(staticDir, "index.html") });
    if (assets.includes(url.pathname)) return route.fulfill({ path: path.join(staticDir, url.pathname) });
    if (url.pathname === "/api/auth/me") return route.fulfill({ json: actor });
    if (url.pathname === `/api/grades/${id}/history`) {
      if (++historyRequests === 1) {
        firstHistoryRequest.resolve(route);
        return;
      }
      return route.fulfill({ json: [revision] });
    }
    const items = url.pathname === "/api/grades" ? [grade] : [];
    return route.fulfill({ json: { items, totalPages: items.length } });
  });

  await page.goto(origin);
  await page.locator('#navigation a[href="#/grades"]').click();
  const historyButton = page.getByRole("button", { name: "Historia oceny", exact: true });
  await historyButton.click();
  const dialog = page.getByRole("dialog", { name: "Historia oceny" });
  await dialog.getByText("Wczytywanie historii…").waitFor();
  const pendingHistory = await firstHistoryRequest.promise;
  await page.goBack();
  await page.waitForURL(origin + "/#/grades");
  await dialog.waitFor({ state: "hidden" });
  assert.equal(await page.evaluate(() => window.lastHistorySignal.aborted), true);
  await pendingHistory.fulfill({ json: [] });

  await page.goForward();
  await page.waitForURL(origin + "/#/grades/history/" + id);
  await dialog.getByText("Dodanie", { exact: true }).waitFor();
  await page.keyboard.press("Escape");
  await dialog.waitFor({ state: "hidden" });
  await page.waitForURL(origin + "/#/grades");

  await historyButton.click();
  await dialog.getByRole("button", { name: "Zamknij okno" }).click();
  await page.waitForURL(origin + "/#/grades");
  await dialog.waitFor({ state: "hidden" });
  assert.deepEqual(errors, []);
});
