package dev.schooldiary.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.modules.syntax.ModuleRuleDefinition.modules;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.modules.ArchModules;
import dev.schooldiary.diary.internal.application.IllegalDependencies;
import jakarta.persistence.Entity;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;

class ArchitectureTests {
  private static final String ROOT = "dev.schooldiary.";
  private static final Set<String> BUSINESS = Set.of("organization", "identity", "diary");
  private static final Map<String, Set<String>> DIRECTIONS =
      Map.of(
          "organization", Set.of("shared", "generated"),
          "identity", Set.of("organization", "shared", "generated"),
          "diary", Set.of("identity", "organization", "shared", "generated"),
          "shared", Set.of(),
          "platform", Set.of("identity", "shared"),
          "bootstrap", Set.of("organization", "identity", "diary", "shared"));

  // Existing managed JPA relationships and the deliberately narrow reference bridges.
  // No repository, service, arbitrary domain type, or whole package is exempted.
  private static final Set<String> JPA_BRIDGES =
      Set.of(
          "identity->dev.schooldiary.organization.internal.domain.School",
          "diary->dev.schooldiary.identity.internal.domain.User",
          "diary->dev.schooldiary.organization.internal.domain.School",
          "diary->dev.schooldiary.organization.internal.domain.AcademicYear",
          "diary->dev.schooldiary.organization.internal.domain.SchoolClass");

  // The single demo composition root wires use cases; it does not get repository access.
  private static final Set<String> DEMO_TYPES =
      Set.of(
          "organization.internal.application.SchoolService",
          "organization.internal.application.AcademicService",
          "organization.internal.application.SchoolCommand",
          "organization.internal.application.SchoolView",
          "organization.internal.application.AcademicYearCommand",
          "organization.internal.application.AcademicYearView",
          "organization.internal.application.ClassCommand",
          "organization.internal.application.ClassView",
          "identity.internal.application.UserService",
          "identity.internal.application.UserCommand",
          "identity.internal.application.UserView",
          "diary.internal.application.SubjectService",
          "diary.internal.application.SubjectCommand",
          "diary.internal.application.SubjectView",
          "diary.internal.application.EnrollmentService",
          "diary.internal.application.EnrollmentCommand",
          "diary.internal.application.EnrollmentView",
          "diary.internal.application.GradeService",
          "diary.internal.application.GradeCommand",
          "diary.internal.application.GradeView",
          "organization.internal.domain.SchoolType",
          "diary.internal.domain.Score");

  private static final JavaClasses PRODUCTION =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .withImportOption(location -> !location.contains("/dev/schooldiary/generated/"))
          .importPackages("dev.schooldiary");

  private static final ArchRule INTERNALS =
      classes()
          .should(
              new ArchCondition<>(
                  "cross module boundaries only through public contracts or the documented JPA bridges") {
                @Override
                public void check(JavaClass source, ConditionEvents events) {
                  for (var dependency : source.getDirectDependenciesFromSelf()) {
                    var target = dependency.getTargetClass();
                    String origin = module(source), destination = module(target);
                    if (origin.isEmpty()
                        || destination.isEmpty()
                        || origin.equals(destination)
                        || !target.getPackageName().contains(".internal.")) continue;
                    boolean jpa = JPA_BRIDGES.contains(origin + "->" + target.getName());
                    boolean demo =
                        source.getName().equals(ROOT + "bootstrap.DemoDataInitializer")
                            && DEMO_TYPES.contains(target.getName().substring(ROOT.length()));
                    if (!jpa && !demo)
                      events.add(
                          SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                  }
                }
              });

  private static final ArchRule NO_TRANSPORT =
      noClasses()
          .that()
          .resideInAnyPackage(
              "..internal.application..",
              "..internal.domain..",
              "..internal.persistence..",
              ROOT + "shared..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..internal.web..",
              ROOT + "generated..",
              ROOT + "platform..",
              "org.springframework.http..",
              "org.springframework.web..",
              "jakarta.servlet..");

  @Test
  void detectsTheThreeIntendedBusinessModulesAndTheirRealEntities() {
    var business =
        PRODUCTION.that(
            DescribedPredicate.describe(
                "business modules", clazz -> BUSINESS.contains(module(clazz))));
    ArchModules<?> detected =
        ArchModules.defineByPackages("dev.schooldiary.(*)..").modularize(business);
    assertThat(detected).hasSize(3);
    BUSINESS.forEach(name -> assertThat(detected.getByIdentifier(name)).isNotNull());
    var entities =
        business.stream()
            .filter(clazz -> clazz.isAnnotatedWith(Entity.class))
            .collect(Collectors.groupingBy(ArchitectureTests::module, Collectors.counting()));
    assertThat(entities)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("organization", 3L, "identity", 2L, "diary", 7L));
  }

  @Test
  void allProductionPackagesFollowTheDeclaredDependencyDirections() {
    for (JavaClass source : PRODUCTION) {
      String origin = module(source);
      if (origin.isEmpty()) {
        assertThat(source.getName()).isEqualTo(ROOT + "SchoolDiaryApplication");
        continue;
      }
      assertThat(DIRECTIONS).containsKey(origin);
      for (var dependency : source.getDirectDependenciesFromSelf()) {
        String destination = module(dependency.getTargetClass());
        if (!destination.isEmpty() && !destination.equals(origin))
          assertThat(DIRECTIONS.get(origin)).as(dependency.getDescription()).contains(destination);
      }
    }
  }

  @Test
  void productionModulesHaveNoDependencyCycles() {
    modules()
        .definedByPackages("dev.schooldiary.(*)..")
        .should()
        .beFreeOfCycles()
        .check(PRODUCTION);
  }

  @Test
  void moduleInternalsAreProtectedByExplicitContractsAndNarrowJpaExceptions() {
    INTERNALS.check(PRODUCTION);
    var actualBridges =
        PRODUCTION.stream()
            .filter(source -> BUSINESS.contains(module(source)))
            .flatMap(source -> source.getDirectDependenciesFromSelf().stream())
            .filter(dep -> !module(dep.getOriginClass()).equals(module(dep.getTargetClass())))
            .filter(dep -> dep.getTargetClass().isAnnotatedWith(Entity.class))
            .map(dep -> module(dep.getOriginClass()) + "->" + dep.getTargetClass().getName())
            .collect(Collectors.toSet());
    assertThat(actualBridges).containsExactlyInAnyOrderElementsOf(JPA_BRIDGES);
  }

  @Test
  void repositoriesAreOwnedAndAccessedOnlyByTheirOwnModule() {
    classes()
        .that()
        .areAssignableTo(Repository.class)
        .should()
        .resideInAPackage("..internal.persistence..")
        .check(PRODUCTION);
    for (JavaClass source : PRODUCTION)
      for (var dep : source.getDirectDependenciesFromSelf())
        if (dep.getTargetClass().getName().startsWith(ROOT)
            && dep.getTargetClass().getSimpleName().endsWith("Repository"))
          assertThat(module(source))
              .as(dep.getDescription())
              .isEqualTo(module(dep.getTargetClass()));
  }

  @Test
  void applicationAndPersistenceAreIndependentOfHttpAndGeneratedTransportModels() {
    NO_TRANSPORT.check(PRODUCTION);
    noClasses()
        .that()
        .resideInAPackage("..internal.domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..internal.application..", "..internal.persistence..", "..internal.web..")
        .check(PRODUCTION);
    noClasses()
        .that()
        .resideInAPackage("..internal.persistence..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..internal.application..")
        .check(PRODUCTION);
  }

  @Test
  void httpAdaptersDelegateToUseCasesInsteadOfUsingRepositoriesOrJpa() {
    noClasses()
        .that()
        .resideInAPackage("..internal.web..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..internal.persistence..", "jakarta.persistence..")
        .check(PRODUCTION);
    noClasses()
        .that()
        .resideInAPackage("..internal.web..")
        .should()
        .dependOnClassesThat()
        .areAnnotatedWith(Entity.class)
        .check(PRODUCTION);
  }

  @Test
  void foreignJpaReferencesAreNotUsedToMutateAnotherModulesEntities() {
    for (JavaClass source : PRODUCTION) {
      for (var call : source.getMethodCallsFromSelf()) {
        JavaClass target = call.getTarget().getOwner();
        if (!module(source).equals(module(target)) && target.isAnnotatedWith(Entity.class))
          assertThat(call.getTarget().getName()).as(call.getDescription()).doesNotStartWith("set");
      }
    }
  }

  @Test
  void applicationViewsContainMaterializedDataInsteadOfLazyJpaEntities() {
    var views =
        PRODUCTION.stream()
            .filter(clazz -> clazz.getPackageName().endsWith(".internal.application"))
            .filter(clazz -> clazz.getSimpleName().endsWith("View"))
            .toList();
    assertThat(views).hasSize(16);
    for (JavaClass view : views) {
      assertThat(view.reflect().isRecord()).as(view.getName()).isTrue();
      for (var component : view.reflect().getRecordComponents())
        assertNoEntity(component.getGenericType());
    }
  }

  @Test
  void guardsRejectDeliberatelyIllegalRepositoryAndHttpDependencies() {
    var illegal = new ClassFileImporter().importClasses(IllegalDependencies.class);
    assertThat(INTERNALS.evaluate(illegal).hasViolation()).isTrue();
    assertThat(NO_TRANSPORT.evaluate(illegal).hasViolation()).isTrue();
  }

  private static void assertNoEntity(Type type) {
    if (type instanceof Class<?> clazz)
      assertThat(clazz.isAnnotationPresent(Entity.class)).as(clazz.getName()).isFalse();
    if (type instanceof ParameterizedType parameterized)
      for (Type argument : parameterized.getActualTypeArguments()) assertNoEntity(argument);
  }

  private static String module(JavaClass clazz) {
    String name = clazz.getPackageName();
    return name.startsWith(ROOT) ? name.substring(ROOT.length()).split("\\.")[0] : "";
  }
}
