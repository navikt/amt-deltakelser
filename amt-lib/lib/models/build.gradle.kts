plugins {
    alias(libs.plugins.serialization)
    id("amt-lib.conventions")
}

dependencies {
    implementation(libs.jackson.jsr310)

    testImplementation(project(":lib:testing"))
    testImplementation(project(":lib:utils"))
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.assertions.json)
    testImplementation(libs.jackson.kotlin)
}
