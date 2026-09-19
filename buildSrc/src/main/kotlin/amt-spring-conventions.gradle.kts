import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")

    id("org.springframework.boot")
    id("org.jetbrains.kotlin.plugin.spring")
    id("io.spring.dependency-management")
}

val libsWrapper = VersionCatalogWrapper.fromProject(project)

repositories {
    mavenCentral()
    maven { setUrl("https://github-package-registry-mirror.gc.nav.no/cached/maven-release") }
}

dependencies {
    constraints {
        implementation(libsWrapper.getLibrary("lz4.java")) {
            because("Fixes CVE-2026-59949")
        }
    }

    // --- Spring Boot ---
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation("org.springframework.boot:spring-boot-starter-jetty")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.springframework.boot:spring-boot-kafka")
    implementation("org.springframework.boot:spring-boot-restclient")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    implementation(libsWrapper.getLibrary("tools.jackson.module.kotlin"))
    implementation(libsWrapper.getLibrary("kafka.clients"))
    implementation(libsWrapper.getLibrary("flyway.postgres"))
    implementation(libsWrapper.getLibrary("postgresql"))
    implementation(libsWrapper.getLibrary("micrometer.prometheus"))
    implementation(libsWrapper.getLibrary("logstash.encoder"))
    implementation(libsWrapper.getLibrary("unleash"))

    // --- amt-lib ---
    implementation(project(":amt-lib:spring-boot"))
    implementation(project(":amt-lib:models"))
    implementation(project(":amt-lib:utils"))

    // --- Test ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-data-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.boot:spring-boot-restclient-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")

    testImplementation(libsWrapper.getLibrary("kotest.assertions.core"))
    testImplementation(libsWrapper.getLibrary("testcontainers.postgresql"))
    testImplementation(libsWrapper.getLibrary("mockk"))
    testImplementation(libsWrapper.getLibrary("springmockk"))
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xwarning-level=IDENTITY_SENSITIVE_OPERATIONS_WITH_VALUE_TYPE:disabled",
        )
    }
}

ktlint {
    version = libsWrapper.getVersion("ktlint.cli.version")
}

// Spring Boot-apper kjøres via bootJar, ikke den vanlige jar-tasken.
tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs(
        "-Xshare:off",
        "-XX:+EnableDynamicAgentLoading",
    )
    // Lar Testcontainers gjenbruke containere på tvers av Gradle-runs.
    // Containerne har modulspesifikke reuse-labels (se IntegrationTest.kt og RepositoryTestBase.kt),
    // så de deles ikke med andre moduler — unngår f.eks. Flyway V01-checksum-kollisjon.
    environment("TESTCONTAINERS_REUSE_ENABLE", "true")
}
