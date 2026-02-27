plugins {
    id("amt-lib.conventions")
    // TODO alias(libs.plugins.ktlint)
}

dependencies {
    // --- Ktor ---
    implementation(platform(libs.ktor.bom))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.server.core)

    api(project(":amt-lib:lib:models"))
    api(project(":amt-lib:lib:utils"))

    api(libs.caffeine)

    testImplementation(project(":amt-lib:lib:testing"))

    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.serialization.jackson)
}

// TODO ktlint { version = libs.versions.ktlint.cli.version }
