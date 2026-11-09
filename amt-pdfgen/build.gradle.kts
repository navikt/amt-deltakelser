import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
}

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

val javaVersion = versionCatalog.findVersion("java.version").get().requiredVersion

kotlin {
    jvmToolchain(javaVersion.toInt())
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(javaVersion)
    }
}

repositories {
    mavenCentral()
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

dependencies {
    testImplementation(project(":amt-lib:models"))
    testImplementation(project(":amt-felles:intern-api-kontrakter"))
    testImplementation(libs.jsoup)
    testImplementation(libs.pdfgen.core)

    testImplementation(libs.mockk)

    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.assertions.table)

    testImplementation(libs.jackson.module.kotlin)
    testImplementation(libs.jackson.datatype.jsr310)

    testImplementation(libs.kotest.runner.junit5)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
