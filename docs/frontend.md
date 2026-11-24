# Lokalny frontend dziennika

Frontend jest częścią tej samej aplikacji Spring Boot co API. Otwórz [http://localhost:8080/](http://localhost:8080/) po uruchomieniu projektu. Nie wymaga osobnego serwera Node, procesu budowania JavaScript, instalowania pakietów ani konfiguracji CORS.

## Uruchomienie

Zachowaj istniejący plik `.env` i dane dostępowe do PostgreSQL. W katalogu projektu uruchom:

```sh
docker compose up --build -d --wait
```

Alternatywnie użyj opisanego w README profilu `dev` z JDK 25 i lokalnym H2. Frontend działa w obu wariantach pod adresem głównym aplikacji. Po zmianie obrazu odśwież kartę przeglądarki i zaloguj się ponownie, jeżeli poprzednia sesja wygasła.

## Zakres i dane

Panel wizualizuje istniejące zasoby: szkoły, konta, lata szkolne, klasy, przedmioty, zapisy i oceny wraz z historią. Nazwy powiązanych rekordów są odczytywane z API. Formularze wysyłają istniejące JSON-y i aktualną wersję przy edycji, dzięki czemu konflikt równoczesnej zmiany nadal zwraca `409`.

Pulpit pokazuje liczniki, ostatnie oceny, ich rozkład A–F oraz powiązanie szkoła → rok → klasa → przedmiot → zapis → ocena. Każdy obszar ma wyszukiwanie, sortowanie i strony listy. Klient pobiera wszystkie strony widocznych danych z API; wyszukiwanie nie ogranicza się do pierwszych 100 rekordów. Jest to prosta implementacja do lokalnej pracy i prezentacji projektu — bardzo duże zbiory wymagałyby wyszukiwania i selektorów po stronie serwera.

Wpisy powiązane są wybierane po nazwach. Zmiana szkoły filtruje lata, nauczycieli i klasy; wystawianie oceny ogranicza wybór do aktywnych zapisów. Edycja konta z pustym polem hasła nie zmienia hasła. Zamknięcie formularza jest zablokowane w czasie zapisu, aby przerwanie żądania nie ukryło wyniku operacji.

Historia ma własny adres `#/grades/history/{id}`. Po usunięciu oceny panel otwiera zachowaną historię; zapisany adres nadal działa po odświeżeniu strony, o ile konto ma uprawnienia. Lista ocen nie zawiera usuniętych wpisów, ponieważ istniejące API nie udostępnia zbiorczej listy archiwalnych ocen.

Interfejs nie tworzy automatycznie kont, szkół ani przykładowych ocen. Pusty widok oznacza brak dostępnych danych; opcjonalny inicjalizator demonstracji nadal wymaga osobnego włączenia zgodnie z README. Dane widoczne po zalogowaniu pochodzą z API i są ograniczone uprawnieniami bieżącego konta.

Administrator globalny tworzy szkoły i zarządza kontami. Administrator szkoły pracuje w obrębie własnej szkoły. Nauczyciel obsługuje zapisy i oceny swoich przedmiotów. Uczeń przegląda swoje dane bez uprawnień do zmian. Ukrycie przycisku w interfejsie jest pomocą dla użytkownika; właściwe reguły dostępu nadal wymusza serwer.

Niektóre nazwy kont nie są udostępniane przez API danej roli. Panel pokazuje wtedy „Niedostępne konto”, zamiast obchodzić reguły dostępu. Nauczyciel widzi w selektorze tylko uczniów już udostępnionych mu przez przedmioty; pierwszy zapis nowego ucznia może wymagać administratora. Są to ograniczenia istniejącego modelu uprawnień, nie dodatkowe uprawnienia frontendu.

## Struktura

```text
src/main/resources/static/
├── index.html
└── ui/
    ├── styles.css   # responsywny wygląd
    ├── api.js       # HTTP, sesje, CSRF, błędy i pobieranie stron
    ├── model.js     # definicje formularzy, wartości i uprawnienia
    └── app.js       # widoki i interakcje użytkownika
```

Kod jest zwykłym HTML/CSS/JavaScript i jest pakowany do istniejącego wykonywalnego JAR oraz obrazu Docker. Moduły JavaScript mają także eksport dla testów Node. Node służy wyłącznie do uruchamiania tych testów, nie do działania aplikacji.

## Sesja i bezpieczeństwo

Strona logowania i jej pliki są publicznie dostępne przez `GET` / `HEAD`. Udostępnienie ich nie otwiera chronionych endpointów `/api/**`. Frontend korzysta z ciasteczka HttpOnly na tym samym originie; nie zapisuje hasła ani tokena dostępu w `localStorage`.

Klient pobiera aktualny CSRF przed żądaniem zmieniającym dane, także przed logowaniem i wylogowaniem. Nie powtarza automatycznie mutacji po błędach. Po wygaśnięciu lub odwołaniu sesji użytkownik musi ponownie się zalogować. Ochrona CSRF, ograniczanie prób logowania, izolacja szkół i blokady wersji pozostają po stronie istniejącego API.

Nie otwieraj `index.html` bezpośrednio jako pliku. Korzystaj ze strony serwowanej przez aplikację i jednego adresu, np. `localhost:8080`; `127.0.0.1` jest osobnym originem i ma oddzielną sesję.

## Sprawdzenia

```sh
./gradlew check integrationTest bootJar --no-daemon
node --test src/test/javascript/*.test.cjs
```

`FrontendWebTests` sprawdza publiczne serwowanie strony/zasobów, zachowanie CSP oraz dalsze odrzucanie niezalogowanych odczytów i zapisów bez CSRF. Testy JavaScript sprawdzają zachowanie klienta API i model formularzy. Testy przeglądarkowe należy wykonywać na osobnej bazie z syntetycznymi kontami, bez zmieniania istniejących danych użytkownika.

## Wykonana weryfikacja

- `./gradlew check integrationTest bootJar --no-daemon`: poprawny wynik, 124 testy lokalne i 33 testy PostgreSQL, bez błędów i pominięć.
- `node --test src/test/javascript/*.test.cjs`: 27 testów, bez błędów i pominięć.
- Chromium z H2 oraz z docelowym obrazem Docker i PostgreSQL: logowanie, szkoła, rok, klasa, konta nauczyciela/ucznia, przedmiot, zapis, ocena, korekta i historia. Konto ucznia nie ma przycisków zapisu.
- Dodatkowe scenariusze UI: wypisanie ucznia; zachowana historia usuniętej oceny po ponownym logowaniu i odświeżeniu adresu; konflikt `409` bez utraty formularza; blokada zamknięcia podczas zapisu; wyszukiwanie w kolekcji przekraczającej 100 rekordów; powrót do logowania po odwołaniu sesji.
- Nazwa zawierająca znaczniki HTML jest tekstem, nie kodem strony. Widok i nawigacja mobilna przeszły sprawdzenie przy szerokości 390 px, bez poziomego przewijania całej strony.
- Dane utworzone przez frontend i historia ocen przetrwały restart aplikacji z PostgreSQL.

Sprawdzenia używały wyłącznie syntetycznych danych: H2 w pamięci i osobnego projektu Compose `diary-frontend-1c8f32e043`. Kontenery oraz wolumen tego testu usunięto; istniejący `school-diary_postgres-data` pozostał zachowany. Nie zmieniano kontraktu OpenAPI, migracji ani istniejącej bazy użytkownika.

Lokalne raporty i zrzuty widoków są w `build/reports/frontend/`, w szczególności `verification.json`, `browser.json`, `browser-edges.json` i `docker/docker-smoke.json`. Sprawdzenia przeglądarkowe wykonano lokalnie; CI uruchamia testy JVM i jednostkowe JavaScript. Nie uruchamiano zdalnego GitHub Actions, publicznego hostingu, testów obciążenia ani nowego skanu Trivy.
