# Audyt aplikacji i przygotowanie do OpenAPI 3.2.1

Data: 29 października 2026. Audyt istniejącego worktree; źródła aplikacji,
kontrakt, migracje i konfiguracja uruchomienia pozostały bez zmian.
Ten dokument opisuje wyniki i proponowane poprawki, a nie wdrożoną migrację.

Aktualizacja z 19 listopada 2026 r. Poprawiono cztery opisane niżej usterki.
Kontrakt 3.0.3 rozróżnia teraz tworzenie i aktualizację, uwzględnia wskazane
pola dopuszczające `null` oraz odrzucanie nieznanych pól żądań. Swagger UI
i endpointy dla klientów korzystają z tego samego źródła. Tabele i próby
poniżej dokumentują stan z dnia audytu. [Obecny kontrakt i jego publikacja](architecture.md#contract-and-errors)

## Wynik

Obecny projekt przechodzi wszystkie istniejące testy, ale dodatkowe próby
ujawniły błędy niewykrywane przez ten zestaw. Najpierw warto poprawić obsługę
równoczesnego usuwania ocen, bootstrap administratora, dwa zachowania UI
i rozbieżności kontraktu.

**Nie wystarczy zmienić `openapi` z `3.0.3` na `3.2.1`.** Obecny generator
Java 7.25.0 odrzuca taką kopię specyfikacji. Swagger UI z tego samego JAR
potrafi ją wyświetlić i obsłużyć logowanie. Wsparcie przeglądarkowego UI
i wsparcie generatora Java to dwie różne rzeczy.

## Potwierdzone błędy

Wszystkie poniższe problemy mają priorytet P2: wymagają poprawki, ale podczas
audytu nie wykazano utraty danych ani nowego obejścia izolacji szkół.

### 1. Wyścig usunięcia oceny kończy się HTTP 500

Miejsce: `src/main/java/dev/schooldiary/diary/internal/application/GradeService.java`,
linie 68–70 i 85–87; ogólna obsługa wyjątków w `platform/web/GlobalExceptionHandler.java`.

Przebieg na rzeczywistym PostgreSQL:

1. Transakcja A usuwa ocenę, zapisuje rewizję DELETED i nadal trzyma blokadę przedmiotu.
2. Transakcja B odczytuje jeszcze widoczną ocenę i czeka na blokadę przedmiotu.
3. A zatwierdza usunięcie. B uzyskuje blokadę i wykonuje `refresh(grade, PESSIMISTIC_WRITE)`.
4. Rekordu już nie ma. Powstaje `jakarta.persistence.EntityNotFoundException`,
   którego obecny handler nie tłumaczy na 404/409; odpowiedzią jest 500.

Odtworzono zarówno DELETE → UPDATE, jak i DELETE → DELETE. Test obserwował
oczekiwanie na blokadę w `pg_stat_activity`, więc nie opierał się na losowym
czasie uśpienia. Dla rzeczywistego wyjątku uruchomiono produkcyjny resolver
HTTP przez MockMvc. Ocena została usunięta, a historia zawierała dokładnie
CREATED i DELETED: ten scenariusz nie uszkodził historii.

Poprawka: po uzyskaniu blokady świadomie obsłużyć zniknięcie rekordu,
np. lokalnie przetłumaczyć wyjątek odświeżenia na `NotFoundException`.
Zachować kolejność blokad przedmiot → ocena/użytkownik i dotychczasowe
sprawdzanie wersji. Nie zmieniać globalnie wszystkich wyjątków JPA na 404,
bo ukryłoby to niezwiązane uszkodzenia relacji. Dodać oba scenariusze do
stałych testów regresji z oczekiwanym 404 albo uzgodnionym 409.

### 2. Bootstrap dopuszcza adres administratora odrzucany przez logowanie

Miejsce: `identity/internal/bootstrap/BootstrapAdmin.java`, linie 37–49.

Warunek `email.contains("@")` akceptuje np. `BOOTSTRAP_ADMIN_EMAIL=@`.
Oddzielna instancja z takim adresem przeszła do gotowości HTTP 200,
lecz logowanie zwróciło HTTP 400 z naruszeniem `@Email`. Powstaje konfiguracja,
która uruchamia aplikację, ale nie daje działającego konta startowego.

Poprawka: normalizować adres, a następnie sprawdzać te same reguły co API:
`@NotBlank`, `@Email`, maksymalnie 50 znaków. Odrzucać konfigurację przy
starcie z czytelnym komunikatem. Dodać testy `@`, `a@@example.test`, zbyt
długiego adresu i poprawnego adresu otoczonego spacjami. Zachować możliwość
całkowitego wyłączenia bootstrapu.

### 3. Zwykłe nazwy są tłumaczone jak wartości enum

Miejsce: `src/main/resources/static/ui/model.js`, linia 124.

`formatValue()` korzysta ze słownika enumów niezależnie od rodzaju pola.
Szkoła o nazwie `ADMIN` jest wyświetlana jako `Administrator`, a `ACTIVE`
jako `Aktywny`. Nazwa `constructor` pobiera odziedziczone pole obiektu
i pojawia się jako `function Object() { [native code] }`.
Dotyczy to także wyszukiwania i sortowania po sformatowanej wartości.
Nie jest to potwierdzone wykonanie kodu ani XSS — interfejs używa tekstowych węzłów DOM.

Reprodukcja:

```bash
node - <<'JS'
const m = require('./src/main/resources/static/ui/model.js');
for (const value of ['ADMIN', 'constructor'])
  console.log(value, '=>', String(m.formatValue('schools', 'name', value, {})));
JS
```

Poprawka: tłumaczyć tylko pola o znanym typie enum, np. `role`, `type`,
`status`, `action`, i używać `Object.hasOwn(enumLabels, value)`.
Nazwy, adresy i inne dowolne teksty zwracać bez takiej translacji.

### 4. Przycisk Wstecz pozostawia otwartą historię oceny

Miejsce: `src/main/resources/static/ui/app.js`, linie 112–122.

Przebieg: Pulpit → Oceny → Historia oceny → Wstecz. Adres wraca do
`#/grades`, lecz dialog nadal ma `open=true`. Kolejne Wstecz pokazuje
pulpit pod tym samym oknem. Potwierdzono w Chromium na rzeczywistym JAR.

Poprawka: synchronizować modal historii z trasą. Opuszczenie adresu
`#/grades/history/{id}` powinno zamykać jego dialog i anulować pobieranie
historii. Zachować blokadę zamykania podczas zapisu i istniejącą ochronę
przed opóźnionymi zdarzeniami `close`. Dodać test przeglądarkowy Wstecz/Dalej.

## Rozbieżności OpenAPI i rzeczywistego HTTP

Źródłem kontraktu jest `src/main/resources/openapi/openapi.json`.
Zawiera 29 schematów i 40 operacji. Poniższe problemy istnieją już w 3.0.3;
nie wynikają z samej migracji.

| Obszar | Kontrakt | Rzeczywiste zachowanie / potrzebna zmiana |
| --- | --- | --- |
| `UserRequest.schoolId`, `UserResponse.schoolId` | `type: string` | Administrator globalny ma `schoolId: null`. Dopuścić null. |
| `SubjectRequest.classId`, `SubjectResponse.classId` | `type: string` | Klasa jest opcjonalna i może być null. |
| `EnrollmentUpdateRequest.leftOn`, `EnrollmentResponse.leftOn` | `type: string` | Aktywny zapis ma null; brak/null daty wypisania uruchamia regułę domyślną. |
| `UserRequest.password` | Pole opcjonalne w jednym schemacie dla POST i PUT | POST bez hasła zwraca 400. PUT bez hasła/null zachowuje hasło. Rozdzielić schematy tworzenia i aktualizacji. |
| `version` w współdzielonych requestach | Pole opcjonalne | PUT bez wersji zwraca 400. Tworzenie nie wymaga wersji. Wymagać wersji w schematach aktualizacji. |
| `UserRequest.enabled` | `type: boolean` | Null oznacza domyślną wartość na POST albo brak zmiany na PUT. Opisać i dopuścić obecną semantykę. |
| Nieznane pola requestów | Domyślnie dozwolone dodatkowe właściwości | Jackson odrzuca je przez `fail-on-unknown-properties: true`. Dodać `additionalProperties: false` do odpowiednich schematów. |
| Opis CSRF | Poleca ręczne kopiowanie i ponowną autoryzację tokena | Swagger UI pobiera go już automatycznie. Rozróżnić UI od ręcznych klientów HTTP. |

Opcjonalność pola oznacza możliwość jego **pominięcia**. Nie oznacza,
że wartość `null` jest poprawna. W OpenAPI 3.2.1 właściwy zapis to np.:

```json
{
  "schoolId": {
    "type": ["string", "null"],
    "format": "uuid"
  }
}
```

Nie dopuszczać null we wszystkich opcjonalnych polach automatycznie:
część z nich ma inne reguły na POST i PUT. Przykładowo wersja może być
pominięta przy tworzeniu, ale musi być nieujemną liczbą na PUT.

Przy rozdzielaniu schematów potrzebne są zmiany odwołań `requestBody`,
ponowna generacja interfejsów/DTO i dostosowanie mapperów HTTP.
Walidacje `@NotBlank`, `@Past`, `@PastOrPresent` przekazywane przez
`x-field-extra-annotation` trzeba zachować. Sam `format` nie zastępuje
walidacji aplikacyjnej. Obecny kontrakt nie zawiera `nullable` ani
boolowskich `exclusiveMinimum`/`exclusiveMaximum`, więc ich masowa konwersja
nie jest tutaj potrzebna. Schematowe `example` warto zastępować `examples`
podczas modernizacji; w nowym standardzie jest przestarzałe, ale samo jego
pozostawienie nie jest powodem 53 błędów generatora.

### Dwa różne dokumenty publiczne

Pomiar rzeczywistego HTTP:

- `/openapi.json`: **3.0.3, 40 operacji**; ten dokument wyświetla Swagger.
- `/v3/api-docs`: **3.1.0, 39 operacji**; brakuje `/api/auth/logout`,
  ponieważ logout obsługuje filtr Spring Security.

Zmiana statycznego kontraktu nie aktualizuje drugiego dokumentu.
Należy wybrać jedno źródło prawdy: publikować ten sam kontrakt pod
obsługiwanymi adresami albo wyłączyć zbędną automatyczną definicję
(np. rozważyć `springdoc.enable-default-api-docs: false`). Sprawdzić przy tym,
że `/v3/api-docs/swagger-config`, initializer i UI nadal działają.
Nie wyłączać bez sprawdzenia całego `springdoc.api-docs.enabled`, od którego
zależy także konfiguracja Swaggera.

## Czy można już przejść na 3.2.1?

OpenAPI 3.2.1 jest opublikowane od 10 września 2026.
[Oficjalna specyfikacja](https://spec.openapis.org/oas/v3.2.1.html).
Numer standardu jest niezależny od `info.version` aplikacji i numeru
biblioteki springdoc; API może nadal mieć `info.version: 1.0.0`.

| Element lokalnego projektu | Wersja | Wynik |
| --- | --- | --- |
| Kontrakt źródłowy | 3.0.3 | Obecny, używany do generacji i przez UI. |
| OpenAPI Generator, generator `spring` | 7.25.0 | Próba z nagłówkiem 3.2.1: odrzucona, **53 błędy**. |
| Java Swagger Parser generatora | 2.1.46 | Nie rozpoznaje właściwie kopii 3.2.1; komunikaty obejmują `openapi is unexpected` i `swagger is missing`. |
| Swagger Core generatora | 2.2.52 | To osobna zależność pluginu, niezależna od runtime aplikacji. |
| springdoc w JAR | 3.1.1 | Runtime generuje `/v3/api-docs` w 3.1.0. |
| Swagger Core w JAR | 2.2.55 | Nie dowodzi wsparcia generacji modeli Java dla 3.2.1. |
| Swagger UI w JAR | 5.32.14 | Kopia 3.2.1: **40 operacji, logowanie 200, `/me` 200**, bez błędów JavaScript. |

Oficjalne informacje są zgodne z próbą lokalną: generator deklaruje
[wsparcie 3.1 jako beta](https://github.com/OpenAPITools/openapi-generator#11---compatibility),
a prace nad [Swagger Parser 3.2](https://github.com/swagger-api/swagger-parser/issues/2248)
i [springdoc 3.2](https://github.com/springdoc/springdoc-openapi/issues/3324)
pozostają otwarte. Swagger UI wprowadził podstawową obsługę 3.2 już w
[wydaniu 5.32.0](https://github.com/swagger-api/swagger-ui/releases/tag/v5.32.0).

Próba kontrolna z samym nagłówkiem **3.1.2** przeszła generację. Porównanie
wygenerowanych źródeł wykazało tylko dwie różnice w przykładach adnotacji
`ErrorResponse`, bez zmian sygnatur. To wynik próby generatora, a nie pełna
weryfikacja migracji 3.1.2 z poprawionymi schematami.

### Zalecana kolejność zmian

1. Poprawić potwierdzone błędy i dodać testy ich scenariuszy.
2. Ujednolicić kontrakt z obecną semantyką null, POST/PUT, walidacji i security;
   zachować endpointy, statusy, enumy, sesje, CSRF i izolację szkół.
3. Zapewnić jeden autorytatywny dokument, walidację kontraktu w CI oraz
   sprawdzanie rzeczywistych requestów i odpowiedzi przeciw schematom.
4. Jako etap przejściowy rozważyć **3.1.2** i wykonać pełne testy po regeneracji.
   Do bezpośredniej generacji z **3.2.1** potrzebne jest wydanie generatora
   i jego parsera ze zweryfikowanym wsparciem tego standardu.
5. Jeżeli publikacja 3.2.1 jest potrzebna wcześniej, alternatywą jest jedna
   źródłowa specyfikacja 3.2.1 i deterministycznie generowany dokument 3.1.2
   wyłącznie dla generatora Java. Taki most wymaga ograniczenia źródła do
   wspólnego podzbioru i testu, który odrzuca konstrukcje tylko z 3.2.
   Nie utrzymywać dwóch ręcznych kontraktów ani nie usuwać nieobsługiwanych
   konstrukcji po cichu. To dodatkowa złożoność, dlatego prostszym wariantem
   dla tego projektu jest etap 3.1.2 i późniejsza bezpośrednia migracja.
6. Po wyborze narzędzi zmienić nagłówek na `3.2.1`, zregenerować kod nadal
   w `build/generated`, porównać sygnatury i walidacje, uruchomić pełny zestaw
   testów i prawdziwe operacje Swaggera/klienta po zalogowaniu.

**Nie używać `skipValidateSpec` jako rozwiązania niezgodności parsera.**
Udane wyświetlenie dokumentu również nie potwierdza poprawności generacji.
Ten projekt nie potrzebuje SSE, QUERY, dodatkowych metod HTTP ani zmian
schematu bazy tylko po to, aby przyjąć nowy numer specyfikacji.

## Wykonane sprawdzenia i ograniczenia

| Sprawdzenie | Wynik |
| --- | --- |
| `./gradlew check integrationTest bootJar --no-daemon --rerun-tasks` | BUILD SUCCESSFUL; 124 testy lokalne + 33 PostgreSQL; 0 błędów, 0 pominiętych. |
| `node --test src/test/javascript/*.test.cjs` | 27/27. |
| Dwa dodatkowe diagnostyczne scenariusze PG usuwania | Oba odtworzyły EntityNotFoundException → 500; dane/historia spójne. |
| Start spakowanego JAR na losowym porcie, osobna H2 w pamięci | Poprawny; anonimowe oceny 401, logowanie 200. |
| Rzeczywisty HTTP: obce pole, brak wersji PUT, brak hasła POST | Każdy 400, potwierdzenie rozbieżności schematów. |
| Osobny start z adresem bootstrapu `@` | Readiness 200, login 400. |
| Chromium na JAR: powrót z historii | Odtworzono otwarty dialog po Wstecz. |
| Chromium: statyczna kopia kontraktu 3.2.1 podstawiona tylko dla testowej strony Swaggera | 40 operacji, logowanie i `/me` 200, brak `pageerror`. |
| Generator na osobnych kopiach w katalogu raportów | 3.1.2 generuje kod; 3.2.1 nie generuje go z powodu błędów walidacji. |
| Końcowy standardowy `check integrationTest bootJar --no-daemon` | BUILD SUCCESSFUL; usunięto tymczasową konfigurację źródeł diagnostycznych. |

Testy diagnostyczne świadomie oczekują obecnego błędu 500 i pozostają poza
`src/test`. Ich zielony wynik oznacza **odtworzenie usterki**, a nie jej naprawę.
Po poprawce stałe testy regresji powinny oczekiwać uzgodnionego 404/409.

Logi, kopie kontraktów, porównanie wygenerowanych źródeł, wyniki HTTP
i kopie skryptów są w `build/reports/audit-openapi/` (katalog ignorowany przez
Git i usuwany przez `clean`). Wszystkie dane użyte podczas prób były
syntetyczne. Nie zmieniano istniejącej bazy, wolumenów ani `.env` użytkownika.

Nie wykonano nowego skanowania CVE/Trivy, testów obciążeniowych, kompletnego
pentestu ani zdalnego CI. Testy przeglądarkowe korzystały ze spakowanego JAR
i izolowanej H2; dodatkowe testy współbieżności i istniejące testy integracyjne
korzystały z prawdziwego PostgreSQL w Testcontainers. Nie uruchamiano ponownie
całego obrazu aplikacji Docker w tym audycie. Nie potwierdzono pełnej obsługi
wszystkich nowych funkcji OpenAPI 3.2.1 — próba dotyczyła obecnego kontraktu
z podmienionym numerem wersji.
