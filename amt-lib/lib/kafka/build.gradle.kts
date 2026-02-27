plugins {
    alias(libs.plugins.serialization)
    id("amt-lib.conventions")
}

dependencies {
    api(libs.kafka.clients)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.logback)
    implementation(project(":lib:utils"))

    testImplementation(project(":lib:testing"))
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.assertions.json)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
}
