import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")

    alias(libs.plugins.spring.boot)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.dependency.management)
}

repositories {
    mavenCentral()
    maven { setUrl("https://github-package-registry-mirror.gc.nav.no/cached/maven-release") }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.springframework.boot:spring-boot-kafka")
    implementation("org.springframework.boot:spring-boot-restclient")

    implementation(libs.tools.jackson.module.kotlin)

    implementation(libs.kafka.clients)

    implementation(libs.flyway.postgres)
    implementation(libs.postgresql)

    implementation(libs.micrometer.prometheus)
    implementation(libs.logstash.encoder)

    implementation(libs.nav.common.log)

    implementation(libs.token.validation.spring)
    implementation(libs.token.client.spring)

    implementation(libs.unleash)

    implementation(project(":amt-felles:visningsnavn"))
    implementation(project(":amt-lib:models"))
    implementation(project(":amt-lib:utils"))

    testImplementation(libs.kotest.assertions.core)

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude("com.vaadin.external.google", "android-json")
    }
    testImplementation("org.springframework.boot:spring-boot-data-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.boot:spring-boot-restclient-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")

    testImplementation(libs.testcontainers.postgresql)

    testImplementation(libs.mockk)
    testImplementation(libs.springmockk)
    testImplementation(libs.token.validation.spring.test)
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
}
