import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")

    alias(libs.plugins.spring.boot)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.dependency.management)
}
kotlin {
    jvmToolchain(25)
}

repositories {
    mavenCentral()
    maven { setUrl("https://github-package-registry-mirror.gc.nav.no/cached/maven-release") }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.springframework.boot:spring-boot-kafka")
    implementation("org.springframework.boot:spring-boot-restclient")

    implementation(libs.tools.jackson.module.kotlin)

    implementation(libs.flyway.postgres)
    implementation(libs.micrometer.prometheus)

    implementation(libs.logstash.encoder)
    implementation(libs.nav.common.audit.log)
    implementation(libs.nav.common.log) {
        exclude("com.squareup.okhttp3", "okhttp")
    }

    implementation(libs.kafka.clients)

    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation(libs.unleash)

    implementation(libs.postgresql)

    implementation(libs.nav.common.rest)

    implementation(project(":amt-lib:spring-boot"))
    implementation(project(":amt-felles:kafka"))
    implementation(project(":amt-lib:models"))
    implementation(project(":amt-lib:utils"))

    implementation(libs.shedlock.spring)
    implementation(libs.shedlock.jdbc.template)

    testImplementation(project(":amt-felles:archunit-test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-data-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.boot:spring-boot-restclient-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")

    testImplementation(libs.kotest.assertions.core)

    testImplementation(libs.testcontainers.postgresql)

    testImplementation(libs.mockk)
    testImplementation(libs.springmockk)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xwarning-level=IDENTITY_SENSITIVE_OPERATIONS_WITH_VALUE_TYPE:disabled",
        )
        jvmTarget = JvmTarget.JVM_25
    }
}

ktlint {
    version = libs.versions.ktlint.cli.version
}

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
