plugins {
    id("amt-felles-conventions")
}

dependencies {
    api(project(":amt-felles:kafka"))

    // --- Metrics ---
    api(libs.micrometer.prometheus)
    implementation(libs.prometheus.metrics.instrumentation)
    implementation(libs.prometheus.metrics.exporter)

    // --- Ktor ---
    implementation(platform(libs.ktor.bom))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.server.core)

    api(libs.bundles.database)

    api(project(":amt-lib:models"))
    api(project(":amt-lib:utils"))

    api(libs.caffeine)

    testImplementation(project(":amt-felles:ktor-test"))

    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.serialization.jackson)
    testImplementation(libs.kotlinx.coroutines.test)
}
