plugins {
    id("amt-felles-conventions")
}

dependencies {
    api(project(":amt-lib:testing"))
    implementation(project(":amt-felles:ktor"))

    implementation(libs.testcontainers.postgresql)
    implementation(libs.testcontainers.kafka)
}
