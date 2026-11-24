# Poprawki po przeglądzie bezpieczeństwa i działania API

Zweryfikowano lokalnie 2026-10-27 w istniejącym worktree `java-app`.

## Zmiany

| Problem | Poprawka i zabezpieczenie |
| --- | --- |
| Stara sesja mogła wskazać nowe konto po ponownym użyciu e-maila | `AccountPrincipal` przechowuje UUID, a `SessionActor` wyszukuje konto wyłącznie po nim. Testy obejmują zmianę/usunięcie konta i ponowne użycie e-maila przez administratora. |
| Reset hasła pozostawiał stare sesje aktywne | Nowy licznik `credentials_version` odwołuje wcześniejsze sesje po zmianie hasła, roli, szkoły lub aktywności konta. Zwykła zmiana profilu/e-maila zachowuje sesję. Test obejmuje równocześnie dwie stare sesje. |
| Ryzyko przechowywania hasha w principal | `AccountPrincipal` implementuje `CredentialsContainer`; test prawdziwego logowania sprawdza, że principal zapisany w sesji ma wymazane hasło. |
| Bootstrap nie dawał się wyłączyć według dokumentacji | Compose przekazuje flagę i dopuszcza brak sekretów przy wyłączonym bootstrapie. Włączony bootstrap z niepoprawnymi danymi zgłasza czytelny błąd. Quickstart zachowuje istniejący `.env`. |
| Ręczne kopiowanie CSRF i niejednoznaczne błędy | Swagger pobiera aktualny token przed każdym zapisem. Nie przekazuje go poza własny origin i nie powtarza automatycznie mutacji. Serwer nadal odrzuca brakujący/nieważny CSRF; komunikaty odróżniają go od odmowy dostępu. |
| Powtarzane odczyty aktora przy historii ocen | Historia rozwiązuje aktora raz i autoryzuje każdą odrębną parę przedmiot–uczeń. Test 100 rewizji wymaga jednego odczytu aktora i jednego odczytu zapisu ucznia; kolejne obce rekordy nadal powodują odmowę. |
| Brak dowodu zachowania blokad przy jednoczesnych zapisach | Nowe testy nakładają transakcje i obserwują oczekiwanie na blokadę w `pg_stat_activity`. Sprawdzają dwie korekty tej samej oceny oraz oba porządki ocena–wypisanie. |

Decyzja o sesjach: [ADR 0006](decisions/0006-stable-session-identity.md).

## Migracja i zgodność

Dodano `014-session-credentials-version.yaml`: niepusta kolumna BIGINT z domyślną wartością `0` dla istniejących kont. Dotychczasowe change sety pozostały bez zmian; do master changelogu dopisano jedynie nowe `include`. Test migracji sprawdza zachowanie danych i wartość licznika po aktualizacji historycznej bazy. OpenAPI, endpointy i DTO pozostały bez zmian.

Stare principal bez UUID wymagają ponownego logowania. Odwołanie wersji sesji jest sprawdzane przy kolejnej operacji API; nie przerywa transakcji, która wcześniej poprawnie sprawdziła uprawnienia. Pełna lista historii ocen pozostaje zgodna z dotychczasowym API — nie dodawano paginacji zmieniającej kontrakt.

## Wykonane sprawdzenia

| Sprawdzenie | Wynik |
| --- | --- |
| `./gradlew check integrationTest bootJar --no-daemon` | BUILD SUCCESSFUL |
| Lokalne testy JVM | 120, bez błędów i pominięć |
| Testy PostgreSQL Testcontainers | 33, bez błędów i pominięć; obejmują regresje prawdziwych sesji |
| `node --test src/test/javascript/*.test.cjs` | 6, bez błędów i pominięć; także dodane do CI |
| ArchUnit i Spotless | Poprawne |
| Obraz Docker, PostgreSQL, rzeczywisty HTTP | Start, gotowość, sesje/CSRF, pełny przepływ dziennika, konflikty wersji, izolacja szkół, historia po restarcie i usunięciu oceny — poprawne |
| Chromium i rzeczywisty Swagger UI | Logowanie → `/me` → utworzenie szkoły → wylogowanie → 401, bez ręcznej autoryzacji CSRF; brak błędów JS |
| Weryfikacja serwerowej ochrony CSRF | Zapis wykonany poza interceptorem bez CSRF nadal odrzucony 403 |
| Wykonywalny JAR i profil dev | Migracje H2, bootstrap, demo, logowanie trzech ról i dane demonstracyjne — poprawne |
| Compose config | Wyłączony bootstrap bez sekretów działa; brak hasła bazy nadal blokuje konfigurację |
| Zachowanie zastanych plików | Wszystkie pozostały obecne; OpenAPI, poprzedni plik migracji i `.DS_Store` mają niezmienione SHA-256 |

Początkowa próba testów PostgreSQL wymagała uruchomienia wyłączonego Dockera. W nowych testach równoległych poprawiono konfigurację aktora dla bezpośrednich wywołań serwisu po MockMvc, zachowując asercje. Test przeglądarkowy dostosowano do dostępnego Chromium i oczekiwania na renderowanie Swaggera. Wyniki w tabeli dotyczą końcowych, zakończonych powodzeniem sprawdzeń.

Test obrazu korzystał z osobnego projektu `diary-fixes-ca83c21ee1`, losowego portu, syntetycznych danych i nowego wolumenu. Zasoby tego testu usunięto, zachowując istniejący `school-diary_postgres-data`. Profil dev testowano na osobnej bazie H2 w pamięci. Istniejąca baza użytkownika nie była używana do testowych zapisów ani resetowana.

Lokalne dowody: `build/reports/fixes/gradle-verification.log`, `docker-smoke.json`, `swagger-browser.json`, `dev-smoke.json`, `verification.json`, raporty JUnit w `build/test-results` i HTML w `build/reports/tests`. Nie uruchamiano zdalnego GitHub Actions, publicznego wdrożenia, testów obciążeniowych ani nowego skanu Trivy. Test Chromium wykonano lokalnie; CI ma testy jednostkowe interceptora i JVM, nie ten pełny scenariusz przeglądarkowy.

## Uruchomienie poprawek

W katalogu projektu wykonaj:

```sh
docker compose up --build -d --wait
```

Nowa migracja wykona się przy starcie. Zachowaj istniejące dane dostępowe do PostgreSQL i wolumen. Po przebudowie odśwież Swagger UI i zaloguj się ponownie przez `POST /api/auth/login`; token CSRF zostanie pobrany automatycznie.
