repositories {
    mavenCentral()
}

plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

dependencies {
    api(project(":amt-lib:lib:kafka"))

    // --- Metrics ---
    api(libs.micrometer.prometheus)
    implementation(libs.prometheus.metrics.instrumentation)
    implementation(libs.prometheus.metrics.exporter)

    // --- Ktor ---
    implementation(platform(libs.ktor.bom))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.server.core)

    api(libs.bundles.database)

    api(project(":amt-lib:lib:models"))
    api(project(":amt-lib:lib:utils"))

    api(libs.caffeine)

    testImplementation(project(":amt-felles:ktor-test"))

    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.serialization.jackson)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(25)
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
