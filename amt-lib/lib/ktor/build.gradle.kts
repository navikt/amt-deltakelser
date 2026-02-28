plugins {
    id("amt-lib.conventions")
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
