import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")

    id("org.springframework.boot")
    id("org.jetbrains.kotlin.plugin.spring")
    id("amt-dependency-management-conventions")
}

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

repositories {
    mavenCentral()
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

dependencies {
    // --- Spring Boot ---
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation("org.springframework.boot:spring-boot-starter-jetty")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.springframework.boot:spring-boot-kafka")
    implementation("org.springframework.boot:spring-boot-restclient")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    implementation(versionCatalog.findLibrary("tools.jackson.module.kotlin").get())
    implementation(versionCatalog.findLibrary("kafka.clients").get())
    runtimeOnly(versionCatalog.findLibrary("flyway.postgres").get())
    runtimeOnly(versionCatalog.findLibrary("postgresql").get())
    runtimeOnly(versionCatalog.findLibrary("micrometer.prometheus").get())
    runtimeOnly(versionCatalog.findLibrary("logstash.encoder").get())
    implementation(versionCatalog.findLibrary("unleash").get())

    // --- nav.commot ---
    runtimeOnly(versionCatalog.findLibrary("nav.common.log").get())

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

    testImplementation(versionCatalog.findLibrary("kotest.assertions.core").get())
    testImplementation(versionCatalog.findLibrary("testcontainers.postgresql").get())
    testImplementation(versionCatalog.findLibrary("mockk").get())
    testImplementation(versionCatalog.findLibrary("springmockk").get())
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
    version = versionCatalog.findVersion("ktlint.cli.version").get().requiredVersion
}

configurations.named("ktlint") {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            requested.version?.let {
                useVersion(it)
                because("ktlint must use the Kotlin versions it was compiled against")
            }
        }
    }
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
