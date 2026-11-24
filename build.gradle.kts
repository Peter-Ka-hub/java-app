plugins {
	java
	jacoco
	id("com.diffplug.spotless") version "8.10.4"
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.openapi.generator") version "7.25.0"
}

group = "dev.schooldiary"
version = "1.0.0"

// Security patches newer than Spring Boot 4.1.1's dependency baseline.
// Keep both Jackson BOMs aligned and remove overrides once Boot manages these fixes.
extra["jackson-2-bom.version"] = "2.21.8"
extra["jackson-bom.version"] = "3.1.8"
extra["tomcat.version"] = "11.0.27"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

val openApiGeneratedSrc = layout.buildDirectory.dir("generated/openapi/src/main/java")

openApiGenerate {
	generatorName.set("spring")
	inputSpec.set("$projectDir/src/main/resources/openapi/openapi.json")
	outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.path)
	apiPackage.set("dev.schooldiary.generated.api")
	modelPackage.set("dev.schooldiary.generated.model")
	configOptions.set(
		mapOf(
			"interfaceOnly" to "true",
			"useTags" to "true",
			"useSpringBoot4" to "true",
			"useJakartaEe" to "true",
			"skipDefaultInterface" to "true",
			"openApiNullable" to "false",
			"dateLibrary" to "java8",
			"generateJsonIncludeAnnotations" to "false",
			"generateJsonSetterNullsAnnotations" to "false",
			"hideGenerationTimestamp" to "true"
		)
	)
}

sourceSets {
	main {
		java {
			srcDir(openApiGeneratedSrc)
		}
	}
}

tasks.named("openApiGenerate") {
    doFirst { delete(layout.buildDirectory.dir("generated/openapi/src/main/java")) }
}

springBoot { mainClass.set("dev.schooldiary.SchoolDiaryApplication") }

tasks.named("compileJava") {
	dependsOn(tasks.named("openApiGenerate"))
}

tasks.named<ProcessResources>("processResources") {
	from("src/main/resources/openapi/openapi.json") {
		into("static")
	}
}

val mapstructVersion = "1.6.3"
dependencies {
	implementation("org.mapstruct:mapstruct:${mapstructVersion}")
	annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")
	annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
	implementation("org.springframework.boot:spring-boot-h2console")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-liquibase")
	implementation("org.springframework.boot:spring-boot-starter-security")
	runtimeOnly("org.postgresql:postgresql")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	compileOnly("org.projectlombok:lombok")
	runtimeOnly("com.h2database:h2")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("com.tngtech.archunit:archunit:1.5.1")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-liquibase-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform { excludeTags("postgres") }
    systemProperty("spring.profiles.active", "test")
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.register<Test>("integrationTest") {
    description = "Runs the PostgreSQL integration suite; Docker is required."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("postgres") }
    systemProperty("spring.profiles.active", "test")
    shouldRunAfter(tasks.test)
}

jacoco { toolVersion = "0.8.15" }
tasks.jacocoTestReport {
    reports { xml.required.set(true); html.required.set(true) }
    classDirectories.setFrom(files(classDirectories.files.map {
        fileTree(it) { exclude("**/generated/**", "**/*MapperImpl*", "**/domain/**") }
    }))
}

spotless {
    java {
        target("src/**/*.java")
        // 1.37 changes Style from an enum to a record; Spotless 8.10.4 still calls Style.valueOf.
        googleJavaFormat("1.36.1")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.bootJar { archiveFileName.set("school-diary.jar") }
