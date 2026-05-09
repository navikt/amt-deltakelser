plugins {
    id("amt-felles-conventions")
}

dependencies {
    api(project(":amt-lib:testing"))
    implementation(project(":amt-felles:ktor"))

    implementation(libs.testcontainers.postgresql)
    implementation(libs.testcontainers.kafka)

    implementation(platform(libs.netty.bom))
    implementation(platform(libs.ktor.bom))

    implementation(project(":amt-lib:utils"))

    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.mock)
    implementation(libs.ktor.serialization.jackson)
    implementation(libs.kotlinx.coroutines.test)
}
