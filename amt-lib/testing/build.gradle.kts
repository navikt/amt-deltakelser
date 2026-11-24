plugins {
    id("amt-lib.conventions")
}

dependencies {
    api(project(":amt-lib:models"))
    api(project(":amt-lib:utils"))
    api(libs.logback.classic)

    api(libs.caffeine)

    api(libs.bundles.kotest)

    api(libs.junit.jupiter.params)
    api(libs.kotlin.test.junit5)
    api(libs.mockk)
}
