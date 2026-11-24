(function (root) {
  "use strict";

  function createCsrfInterceptor(fetchToken, pageUrl) {
    const page = new URL(pageUrl);
    const uiRoot = page.pathname.lastIndexOf("/swagger-ui/");
    if (uiRoot < 0) throw new Error("Swagger UI application path could not be determined");
    const csrfUrl = new URL(page.pathname.slice(0, uiRoot + 1) + "api/auth/csrf", page.origin);
    const safeMethods = new Set(["GET", "HEAD", "OPTIONS", "TRACE"]);

    return async function (request) {
      const target = new URL(request.url, page.href);
      const headers = request.headers || (request.headers = {});

      // Never forward an old manually authorized token to another server.
      for (const name of Object.keys(headers)) {
        if (name.toLowerCase() === "x-csrf-token") delete headers[name];
      }
      if (target.origin !== page.origin) return request;

      request.credentials = "same-origin";
      if (safeMethods.has((request.method || "GET").toUpperCase())) return request;

      // Login/logout rotate CSRF state, so fetch the current token for every write.
      const response = await fetchToken(csrfUrl.href, {
        method: "GET",
        credentials: "same-origin",
        cache: "no-store",
        redirect: "error",
        headers: { Accept: "application/json" }
      });
      if (!response.ok) throw new Error("Could not obtain a CSRF token. Reload Swagger UI and retry.");
      const csrf = await response.json();
      if (typeof csrf.token !== "string" || !csrf.token || csrf.headerName !== "X-CSRF-TOKEN") {
        throw new Error("Invalid CSRF response. Reload Swagger UI and retry.");
      }
      headers[csrf.headerName] = csrf.token;
      return request;
    };
  }

  if (typeof module !== "undefined" && module.exports) {
    module.exports = { createCsrfInterceptor };
  } else {
    root.schoolDiaryCsrfInterceptor = createCsrfInterceptor(root.fetch.bind(root), root.location.href);
  }
})(globalThis);
