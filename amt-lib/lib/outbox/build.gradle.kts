plugins {
    alias(libs.plugins.serialization)
    id("amt-lib.conventions")
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.jackson.kotlin)
    api(project(":lib:utils"))
    api(project(":lib:kafka"))
    api(libs.prometheus.metrics.core)
    implementation(libs.prometheus.metrics.instrumentation)
    implementation(libs.prometheus.metrics.exporter)
    implementation(libs.logback)
    implementation(libs.jackson.jsr310)
    implementation(libs.kotliquery)
    implementation(libs.postgresql)

    testImplementation(project(":lib:testing"))
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.assertions.json)
}
