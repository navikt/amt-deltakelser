group = "no.nav.amt-distribusjon"
version = "1.0-SNAPSHOT"

plugins {
    alias(libs.plugins.kotlin.jvm)
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

    // --- NAV AMT ---
    implementation(libs.bundles.amt)

    // --- POAO ---
    implementation(libs.poao.tilgang.client)

    // --- Varsel ---
    implementation(libs.tms.varsel.kotlin.builder)

    // --- Database ---
    implementation(libs.bundles.database)

    // --- Test ---
    testImplementation(libs.bundles.ktor.test)
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.mockk)
    testImplementation(libs.nimbus.jose.jwt)
    testImplementation(libs.amt.testing)
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

ktlint {
    version = libs.versions.ktlint.cli.version
}

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
