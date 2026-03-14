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
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.springframework.boot:spring-boot-kafka")

    implementation(libs.tools.jackson.module.kotlin)

    implementation(libs.flyway.postgres)
    implementation(libs.micrometer.prometheus)

    implementation(libs.logstash.encoder)
    implementation(libs.nav.common.audit.log)
    implementation(libs.nav.common.log) {
        exclude("com.squareup.okhttp3", "okhttp")
    }

    implementation(libs.kafka.clients)

    implementation(libs.token.validation.spring)
    implementation(libs.token.client.spring)
    implementation(libs.okhttp)
    implementation(libs.caffeine)
    implementation(libs.unleash)

    implementation(libs.postgresql)

    implementation(project(":amt-lib:models"))
    implementation(project(":amt-lib:kafka"))
    implementation(project(":amt-lib:utils"))

    implementation(libs.shedlock.spring)
    implementation(libs.shedlock.jdbc.template)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-data-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")

    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.token.validation.spring.test)

    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)

    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.springmockk)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property",
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
}
