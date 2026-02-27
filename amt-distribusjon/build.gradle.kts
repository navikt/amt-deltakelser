group = "no.nav.amt-distribusjon"
version = "1.0-SNAPSHOT"

plugins {
    kotlin("jvm")
    alias(libs.plugins.ktor)
    alias(libs.plugins.ktlint)
    application
}

repositories {
    mavenCentral()
    maven { setUrl("https://github-package-registry-mirror.gc.nav.no/cached/maven-release") }
}

dependencies {

    // --- Ktor BOM ---
    implementation(platform(libs.ktor.bom))

    // --- Ktor ---
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.client)

    // --- Serialization ---
    implementation(libs.jackson.datatype.jsr310)

    // --- Metrics ---
    implementation(libs.micrometer.prometheus)

    // --- Cache ---
    implementation(libs.caffeine)

    // --- Logging ---
    implementation(libs.bundles.logging)

    // --- amt-lib, lib:ktor drar inn models og utils
    implementation(project(":amt-lib:lib:ktor"))
    implementation(project(":amt-lib:lib:outbox"))

    // --- POAO ---
    implementation(libs.poao.tilgang.client)

    // --- Varsel ---
    implementation(libs.tms.varsel.kotlin.builder)

    // --- Database ---
    implementation(libs.bundles.database)

    // --- Test ---
    testImplementation(project(":amt-lib:lib:testing"))
    testImplementation(libs.bundles.ktor.test)
    testImplementation(libs.nimbus.jose.jwt)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

application {
    mainClass.set("no.nav.amt.distribusjon.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

ktlint { version = libs.versions.ktlint.cli.version }

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs(
        "-Xshare:off",
        "-XX:+EnableDynamicAgentLoading",
    )
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Main-Class" to "no.nav.amt.distribusjon.ApplicationKt",
        )
    }
}
