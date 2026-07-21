plugins {
    id("amt-felles-conventions")
}

dependencies {
    api(project(":amt-lib:models"))

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.kotest.assertions.core)
}
