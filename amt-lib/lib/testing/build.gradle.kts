plugins {
    id("amt-lib.conventions")
}

dependencies {
    implementation(project(":amt-lib:lib:models"))
    api(project(":amt-lib:lib:utils"))
    implementation(project(":amt-lib:lib:outbox"))

    implementation(libs.kafka.clients)
    implementation(libs.logback.classic)

    implementation(libs.testcontainers.kafka)
    implementation(libs.testcontainers.postgresql)

    implementation(libs.bundles.database)

    implementation(libs.caffeine)

    api(libs.bundles.kotest)

    api(libs.junit.jupiter.params)
    api(libs.kotlin.test.junit5)
    api(libs.mockk)
}
