plugins {
    id("amt-felles-conventions")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.jackson.module.kotlin)
}
