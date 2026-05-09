plugins {
    id("amt-felles-conventions")
}

dependencies {
    constraints {
        implementation(libs.jackson.core) {
            because("GHSA-72hv-8253-57qq")
        }
    }

    api(project(":amt-felles:kafka"))

    // --- Metrics ---
    api(libs.micrometer.prometheus)
    api(libs.prometheus.metrics.instrumentation)
    api(libs.prometheus.metrics.exporter)

    // --- Ktor ---
    implementation(platform(libs.ktor.bom))
    implementation(platform(libs.netty.bom))

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.auth.jwt)

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
