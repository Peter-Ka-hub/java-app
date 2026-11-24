(function (root) {
  "use strict";

  const safeMethods = new Set(["GET", "HEAD", "OPTIONS", "TRACE"]);

  class ApiError extends Error {
    constructor(status, message, details = {}) {
      super(message);
      this.name = "ApiError";
      this.status = status;
      this.errors = Array.isArray(details.errors)
        ? details.errors.filter((entry) => typeof entry === "string")
        : [];
      this.serverMessage = typeof details.serverMessage === "string" ? details.serverMessage : "";
      this.retryAfter = details.retryAfter ?? null;
    }
  }

  function createApi(options = {}) {
    const fetchImpl = options.fetchImpl || (root.fetch && root.fetch.bind(root));
    if (typeof fetchImpl !== "function") throw new TypeError("Brak obsługi żądań HTTP w przeglądarce.");
    const onUnauthorized = options.onUnauthorized || (() => {});
    if (typeof onUnauthorized !== "function") throw new TypeError("onUnauthorized musi być funkcją.");

    const location = new URL(root.location?.href || "http://localhost/");
    const uiIndex = location.pathname.lastIndexOf("/ui/");
    const contextPath = uiIndex < 0 ? "/" : location.pathname.slice(0, uiIndex + 1);
    const base = new URL(options.baseUrl || contextPath, location.origin);
    if (root.location && base.origin !== location.origin)
      throw new TypeError("API musi działać pod tym samym adresem co interfejs.");
    if (base.username || base.password || base.search || base.hash)
      throw new TypeError("Nieprawidłowy adres bazowy API.");
    const prefix = base.pathname.replace(/\/$/, "") + "/";
    const apiPrefix = prefix + "api/";

    function endpoint(path) {
      if (typeof path !== "string" || !path) throw new TypeError("Podaj ścieżkę API.");
      // Leading /api paths are relative to the application's context, not another server.
      const candidate = path.startsWith("/api/") ? prefix + path.slice(1) : path;
      const target = new URL(candidate, base.origin + prefix);
      if (target.origin !== base.origin || target.username || target.password || !target.pathname.startsWith(apiPrefix))
        throw new TypeError("Dozwolone są wyłącznie żądania do API tej aplikacji.");
      target.hash = "";
      return target;
    }

    function checkAbort(signal) {
      if (!signal?.aborted) return;
      if (signal.reason) throw signal.reason;
      const error = new Error("Żądanie zostało przerwane.");
      error.name = "AbortError";
      throw error;
    }

    function retryAfter(response) {
      const value = response.headers?.get("Retry-After");
      if (!value) return null;
      if (/^\d+$/.test(value.trim())) return Number(value.trim());
      const date = Date.parse(value);
      return Number.isFinite(date) ? Math.max(0, Math.ceil((date - Date.now()) / 1000)) : null;
    }

    function errorMessage(status, body, target, wait) {
      if (status === 401) return target.pathname.endsWith("/auth/login")
        ? "Nieprawidłowy e-mail lub hasło albo konto jest nieaktywne."
        : "Sesja wygasła lub została unieważniona. Zaloguj się ponownie.";
      if (status === 403) return /csrf/i.test(body.message || "")
        ? "Nie udało się potwierdzić sesji. Odśwież stronę i spróbuj ponownie."
        : "Nie masz uprawnień do tej operacji.";
      if (status === 409)
        return "Operacja jest w konflikcie z aktualnymi danymi. Odśwież widok i sprawdź powiązania rekordów.";
      if (status === 429) return wait === null
        ? "Zbyt wiele prób logowania. Odczekaj chwilę i spróbuj ponownie."
        : `Zbyt wiele prób logowania. Spróbuj ponownie za ${wait} s.`;
      if (status === 400) return "Sprawdź dane formularza i popraw wskazane pola.";
      if (status === 404) return "Nie znaleziono rekordu. Odśwież widok.";
      if (status >= 500) return "Wystąpił błąd serwera. Spróbuj ponownie później.";
      return "Nie udało się wykonać operacji.";
    }

    async function perform(target, init) {
      checkAbort(init.signal);
      let response;
      try {
        response = await fetchImpl(target.href, {
          ...init,
          credentials: "same-origin",
          cache: "no-store",
          redirect: "error"
        });
      } catch (error) {
        if (init.signal?.aborted || error?.name === "AbortError") throw error;
        throw new ApiError(0, "Nie udało się połączyć z aplikacją. Sprawdź połączenie i spróbuj ponownie.");
      }
      checkAbort(init.signal);
      if (response.status === 204) return null;
      let body;
      try {
        body = await response.json();
      } catch (error) {
        if (init.signal?.aborted || error?.name === "AbortError") throw error;
        body = null;
      }
      checkAbort(init.signal);
      if (!response.ok) {
        const details = body && typeof body === "object" ? body : {};
        const wait = retryAfter(response);
        const error = new ApiError(response.status, errorMessage(response.status, details, target, wait), {
          errors: details.errors,
          serverMessage: details.message,
          retryAfter: wait
        });
        if (response.status === 401) onUnauthorized(error);
        throw error;
      }
      if (body === null)
        throw new ApiError(0, "Aplikacja zwróciła nieprawidłową odpowiedź. Odśwież stronę i spróbuj ponownie.");
      return body;
    }

    async function request(path, { method = "GET", body, signal } = {}) {
      const target = endpoint(path);
      const verb = method.toUpperCase();
      const headers = { Accept: "application/json" };
      checkAbort(signal);
      if (!safeMethods.has(verb)) {
        const csrf = await perform(endpoint("/api/auth/csrf"), {
          method: "GET", signal, headers: { Accept: "application/json" }
        });
        if (!csrf || csrf.headerName !== "X-CSRF-TOKEN" || typeof csrf.token !== "string" || !csrf.token)
          throw new ApiError(0, "Nie udało się pobrać tokena sesji. Odśwież stronę.");
        headers[csrf.headerName] = csrf.token;
      }
      const init = { method: verb, headers, signal };
      if (body !== undefined) {
        headers["Content-Type"] = "application/json";
        init.body = JSON.stringify(body);
      }
      return perform(target, init);
    }

    function resourceUrl(resource) {
      if (typeof resource !== "string" || !resource) throw new TypeError("Podaj zasób API.");
      return endpoint(/^[a-z][a-z-]*$/.test(resource) ? "/api/" + resource : resource);
    }

    function page(resource, pageNumber = 0, size = 20, { signal } = {}) {
      if (!Number.isInteger(pageNumber) || pageNumber < 0 || !Number.isInteger(size) || size < 1 || size > 100)
        throw new RangeError("Nieprawidłowy numer lub rozmiar strony.");
      const target = resourceUrl(resource);
      target.searchParams.set("page", pageNumber);
      target.searchParams.set("size", size);
      return request(target.href, { signal });
    }

    async function all(resource, { signal } = {}) {
      const records = [];
      let pages = 1;
      for (let number = 0; number < pages; number++) {
        checkAbort(signal);
        const result = await page(resource, number, 100, { signal });
        if (!result || !Array.isArray(result.items) || !Number.isInteger(result.totalPages) || result.totalPages < 0)
          throw new ApiError(0, "Aplikacja zwróciła nieprawidłową listę danych.");
        if (number === 0) pages = result.totalPages;
        records.push(...result.items);
      }
      return records;
    }

    return Object.freeze({
      request,
      me: () => request("/api/auth/me"),
      login: (email, password) => request("/api/auth/login", { method: "POST", body: { email, password } }),
      logout: () => request("/api/auth/logout", { method: "POST" }),
      page,
      all
    });
  }

  const exports = Object.freeze({ createApi, ApiError });
  if (typeof module !== "undefined" && module.exports) module.exports = exports;
  else root.DiaryApi = exports;
})(globalThis);
