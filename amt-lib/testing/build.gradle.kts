plugins {
    id("amt-lib.conventions")
}

dependencies {
    implementation(project(":amt-lib:models"))
    api(project(":amt-lib:utils"))

    implementation(libs.kafka.clients)
    implementation(libs.logback.classic)

    implementation(libs.testcontainers.kafka)

    implementation(libs.caffeine)

    api(libs.bundles.kotest)

    api(libs.junit.jupiter.params)
    api(libs.kotlin.test.junit5)
    api(libs.mockk)
    api(libs.kotlinx.coroutines.test)
}
