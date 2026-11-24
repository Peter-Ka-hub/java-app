# Refaktor struktury: moduły biznesowe

Data weryfikacji: 2026-10-27. Zakres: istniejący worktree `java-app`, bez podmiany kodu z innych katalogów, aktualizacji stosu wykonawczego ani zmian funkcjonalnych API.

## Dlaczego trzy moduły

Analiza kodu przed przeniesieniem pokazała następujące zależności:

| Obszar | Zależności i reguły | Decyzja |
| --- | --- | --- |
| Szkoła, rok, klasa | Klasa musi należeć do tej samej szkoły co rok. Zmiana dat/struktury jest ograniczona po przypisaniu przedmiotów. | Jeden moduł `organization`. |
| Konto | Szkoła, rola i aktywność wyznaczają uprawnienia. Zmiana roli/szkoły albo usunięcie konta nie może unieważnić zapisów i historii ocen. | Osobny `identity`; zależy od struktury szkoły, a o wykorzystanie konta pyta przez swój kontrakt. |
| Przedmiot, zapis, ocena, historia | Przedmiot wskazuje nauczyciela, szkołę, rok i opcjonalnie klasę. Ocena wymaga właściwego ucznia, zapisu i daty. Wypisanie uwzględnia daty ocen i ich zmian. Mutacje współdzielą transakcje i blokady. | Jeden moduł `diary`; osobny moduł dla każdej encji zwiększyłby wzajemne zależności. |

Kod źródłowy zależy w kierunku `diary → identity → organization`, dodatkowo `diary → organization`. Diagram i dokładne kontrakty znajdują się w [opisie architektury](architecture.md), a uzasadnienie i alternatywy w [ADR 0005](decisions/0005-business-module-boundaries.md).

## Docelowe drzewo

```text
src/main/java/dev/schooldiary/
├── SchoolDiaryApplication.java
├── organization/
│   ├── AcademicUsage.java
│   ├── SchoolReferences.java
│   └── internal/
│       ├── web/           # szkoły, lata, klasy + mapowanie HTTP
│       ├── application/   # SchoolService, AcademicService, commands/views, mapowanie JPA
│       ├── domain/        # School, AcademicYear, SchoolClass, SchoolType
│       └── persistence/   # repozytoria, JpaSchoolReferences
├── identity/
│   ├── AccountLookup.java
│   ├── AccountCredentials.java
│   ├── AccountUsage.java
│   ├── TeacherAccountScope.java
│   └── internal/
│       ├── web/           # UserController, AuthController, UserHttpMapper
│       ├── application/   # UserService, UserCommand, UserView, UserMapper
│       ├── domain/        # User
│       ├── persistence/   # UserRepository, UserScopes, JpaAccountLookup
│       ├── authentication/# SessionActor
│       └── bootstrap/     # BootstrapAdmin
├── diary/
│   └── internal/
│       ├── web/           # przedmioty, zapisy, oceny + mapowanie HTTP
│       ├── application/   # przypadki użycia, DiaryAccessPolicy, commands/views, mapowanie JPA
│       ├── domain/        # Subject, Enrollment, Grade, GradeRevision + enumy
│       └── persistence/   # repozytoria, zakresy zapytań, implementacje kontraktów wykorzystania
├── shared/
│   ├── access/            # Actor, CurrentActor, UserRole
│   ├── application/       # PageResult
│   ├── concurrency/       # Versions
│   └── error/             # NotFoundException, ConflictException
├── platform/
│   ├── security/          # konfiguracja Spring Security, ogranicznik logowania
│   └── web/               # błędy HTTP, logowanie żądań, ping
└── bootstrap/             # DemoDataInitializer — składanie danych demonstracyjnych

build/generated/
├── openapi/src/main/java/dev/schooldiary/generated/
│   ├── api/               # generowane interfejsy HTTP
│   └── model/             # generowane DTO
└── sources/annotationProcessor/java/main/dev/schooldiary/
    └── ...                # implementacje MapStruct

src/test/java/dev/schooldiary/
├── architecture/          # reguły ArchUnit
├── identity/internal/application/ # izolowany test przypadku użycia
├── diary/internal/application/    # celowo błędna próbka wyłącznie dla testu reguł
├── platform/security/     # test ogranicznika logowania
└── ...                    # zachowane testy API, workflow, migracji i PostgreSQL
```

`diary` nie potrzebuje obecnie osobnego publicznego serwisu/fasady: moduły niższe nie wywołują jego przypadków użycia. Implementuje kontrakty pytań zdefiniowane przez `identity` i `organization`. Nie utworzono pustych katalogów ani dodatkowych projektów Gradle.

## Mapa najważniejszych przeniesień

W tabeli pominięto wspólny stary prefiks `com.example.demo` i nowy `dev.schooldiary`.

| Poprzednio | Obecnie |
| --- | --- |
| `DemoApplication` | `SchoolDiaryApplication` |
| `app.SchoolService`, `app.AcademicService` | `organization.internal.application` |
| `domain.school`, `domain.academic`, `domain.schoolclass` | encje w `organization.internal.domain`, repozytoria w `organization.internal.persistence` |
| `app.UserService`, `domain.user.User` | `identity.internal.application.UserService`, `identity.internal.domain.User` |
| `infrastructure.ports.UserRepository` | `identity.internal.persistence.UserRepository` |
| `app.SubjectService`, `app.EnrollmentService`, `app.GradeService` | `diary.internal.application` |
| `domain.subject`, `domain.enrollment`, `domain.grade` | encje w `diary.internal.domain`, repozytoria w `diary.internal.persistence` |
| `api.<obszar>.*Controller` | `<właściciel>.internal.web` |
| `api.<obszar>.*Mapper` | mapowanie encji w `internal.application.*Mapper`, mapowanie transportu w `internal.web.*HttpMapper` |
| `security.AccessPolicy` | `identity.internal.authentication.SessionActor`, `shared.access.CurrentActor` / `Actor`, `diary.internal.application.DiaryAccessPolicy` |
| `app.ScopedQueries` | `identity.internal.persistence.UserScopes`, `diary.internal.persistence.SubjectScopes` / `GradeScopes`, kontrakt `TeacherAccountScope` i jego implementacja w `diary` |
| `security.SecurityConfiguration`, `security.LoginRateLimitFilter` | `platform.security` |
| `security.BootstrapAdmin` | `identity.internal.bootstrap.BootstrapAdmin` |
| `api.common.*Exception`, `app.Versions` | `shared.error`, `shared.concurrency` |
| `api.common.GlobalExceptionHandler`, `infrastructure.RequestLoggingFilter` | `platform.web` |
| `app.DemoDataInitializer` | `bootstrap.DemoDataInitializer` |
| `api.generated` / `api.generated.model` | `generated.api` / `generated.model`, wyłącznie w `build/generated` |

Pełna mapa klas jest zapisana w [refactor-class-moves.json](refactor-class-moves.json). Tablica jako wartość oznacza rozdzielenie odpowiedzialności jednej starej klasy między kilka nowych.

## Co zmieniło się w zależnościach

Kontroler tworzy komendę aplikacyjną, wywołuje serwis i zamienia jego widok na odpowiedź HTTP. Serwis nie importuje wygenerowanych DTO, mapperów web ani wyjątków HTTP. Mapowanie z encji do widoku odbywa się w transakcji, zanim kontroler otrzyma wynik. Dzięki temu wyłączone Open Session in View nadal działa poprawnie przy relacjach LAZY.

Repozytorium kont jest szczegółem `identity`, a nie „portem” infrastruktury. Dziennik pobiera zarządzane referencje kont i zakłada istniejące blokady przez `AccountLookup`. Analogicznie `SchoolReferences` obsługuje referencje struktury szkoły. Adaptery tych kontraktów wymagają trwającej transakcji (`MANDATORY`), zachowując jej granicę, kolejność blokad i `refresh`.

Gdy konto ma zmienić rolę, `identity` pyta własny kontrakt `AccountUsage`, czy istnieją powiązane dane. Odpowiada `DiaryAccountUsage` przez własne repozytoria. `identity` nie musi importować repozytorium ocen. Ten sam mechanizm zastosowano do wykorzystania lat/klas. Zakres uczniów widocznych nauczycielowi pozostaje filtrem SQL, dostarczanym przez `TeacherAccountScope`; nie pobieramy najpierw całej listy ID do pamięci.

## Kontrola architektury i kompromisy

Dziesięć testów ArchUnit jest częścią `test` / `check`. Narzędzie rozpoznaje dokładnie trzy moduły biznesowe i ich rzeczywiste encje: 3 w `organization`, 1 w `identity`, 4 w `diary`. Sprawdza kierunki, brak cykli między modułami, dostęp do `internal`, własność repozytoriów, brak zależności aplikacji od HTTP, kierunek domain/persistence/application oraz brak encji w widokach. Obejmuje także implementacje MapStruct. Próbka celowo błędnego kodu potwierdza, że naruszenia są wykrywane.

Nie deklarujemy pełnej izolacji danych. Pozostają dokładnie pięć dopuszczonych par moduł → obca encja JPA: `identity → School`, `diary → User`, `diary → School`, `diary → AcademicYear`, `diary → SchoolClass`. Reguła wymaga zgodności rzeczywistych odwołań z tą listą i zabrania wywoływania setterów obcych encji. Repozytoria nie są objęte wyjątkiem. Osobny wyjątek dopuszcza wyłącznie konkretne typy używane przez `DemoDataInitializer` jako miejsce składania demonstracji.

Kontrakt zakresu nauczyciela nadal zna JPA Criteria, a `shared` zna wybrane typy Spring Security i Spring Data. Są to jawne zależności tego monolitu. Usunięcie relacji między encjami wymagałoby osobnego projektu persystencji, spójności i blokad, wykraczającego poza ten refaktor.

## Weryfikacja

| Sprawdzenie | Wynik |
| --- | --- |
| Stan początkowy, rzeczywiste wykonanie z `--rerun-tasks` | 91 testów lokalnych + 22 PostgreSQL, 0 błędów i pominięć |
| `./gradlew check integrationTest bootJar --no-daemon` po refaktorze | 102 lokalne + 22 PostgreSQL; 0 błędów i pominięć; Spotless i bootJar poprawne |
| Nowe testy | 10 kontroli architektury + 1 izolowany przypadek zmiany roli konta |
| Zachowanie dotychczasowych testów | Treść testów i asercje identyczne po pominięciu importów, pakietów i zmiany nazwy klasy startowej |
| Porównanie SHA-256 z kopią stanu początkowego | OpenAPI, oba pliki Liquibase, Dockerfile, Compose i zastany `.DS_Store` bez zmian |
| Wykonywalny JAR | `Start-Class: dev.schooldiary.SchoolDiaryApplication`; brak klas `com.example.demo`; wygenerowany kontrakt w nowym pakiecie |
| Obraz Docker + PostgreSQL 18.6 | Wieloetapowy build i readiness poprawne; rzeczywisty przepływ HTTP opisany poniżej |
| Profil `dev`, JAR, H2 w pamięci | Start, migracje i opcjonalne dane demo poprawne; logowanie admina/nauczyciela/ucznia, klasa 1A, przedmiot, zapis i ocena B |

Pierwsza próba początkowego zestawu PostgreSQL wymagała uruchomienia wyłączonego Dockera. Po uruchomieniu demona ponowiono zestaw i zapisano kompletny zielony wynik przed refaktorem. Nie zmieniano testów z powodu niedostępności środowiska.

Test obrazu używał nowego projektu Compose `diary-refactor-c4d252b810`, losowego portu, syntetycznych kont i nowej bazy. Potwierdził:

- readiness, identyczny `/openapi.json`, 401 dla niezalogowanego i 403 bez CSRF;
- logowanie przez cookies i CSRF oraz wylogowanie;
- utworzenie szkoły, roku, klasy, kont, przedmiotu i zapisu;
- listę uczniów nauczyciela, ocenę, korektę, autora historii i 409 przy starej wersji;
- odczyt ucznia, zabroniony zapis i izolację administratora szkoły;
- zachowanie wersji oceny i całej historii w PostgreSQL po restarcie aplikacji;
- wypisanie, usunięcie oceny z zachowaniem historii i 409 przy usuwaniu powiązanych zasobów.

Istniejący wolumen `school-diary_postgres-data` pozostał zachowany. Usunięto wyłącznie kontenery, sieć i nowy wolumen tego testu. Nie resetowano istniejącej bazy.

Lokalne dowody znajdują się w `build/reports/refactor/`: `gradle-verification.log`, `docker-smoke.json`, `docker.log`, `dev-smoke.json`, `dev.log` i `preservation.json`; raporty testów pozostają w `build/reports/tests`. Kopia zastanego worktree i wyniki początkowe są w prywatnym katalogu tymczasowym `/private/tmp/school-diary-structure-baseline-5vmnxixw`. To lokalne artefakty audytu, nie pliki do publikowania.

Ograniczenia: nie uruchamiano z tego worktree GitHub Actions, publicznego wdrożenia, testu obciążenia ani nowego skanu Trivy. Historyczny skan jest opisany w [wcześniejszym raporcie](verification.md); nie przedstawiamy go jako skanu wykonanego po tym refaktorze. Test uruchomienia nie podłączał się do istniejącej bazy użytkownika. Zgodność migracji dodatkowo sprawdzają zachowane testy migracji świeżej i historycznej bazy.

## Jak opowiedzieć o zmianie na rozmowie

„Zacząłem od działających testów i rzeczywistych zależności. Połączyłem przedmioty, zapisy, oceny i historię, bo współdzielą reguły i transakcje. Konta oraz strukturę szkoły wydzieliłem jako osobne obszary. Usunąłem zależność serwisów od HTTP i dostęp do cudzych repozytoriów. Gdy potrzebne jest pytanie w przeciwnym kierunku, właściciel przypadku użycia definiuje kontrakt, a właściciel danych go implementuje. Testy architektury pilnują granic. Zachowałem relacje JPA i otwarcie opisuję, że to modularny monolit ze wspólną persystencją, a nie pełna izolacja modeli.”

Prześledź `UserService.update` → `AccountUsage` → `DiaryAccountUsage`, potem jego izolowany test. Następnie przeczytaj `GradeController` → `GradeHttpMapper` → `GradeService` → `GradeMapper`: pierwszy mapper zna HTTP, drugi pracuje z encją wewnątrz transakcji. Te przykłady pokazują praktyczny sens granic i testowalności.
