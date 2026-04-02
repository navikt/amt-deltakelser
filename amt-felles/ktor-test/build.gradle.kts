plugins {
    id("amt-felles-conventions")
}

dependencies {
    api(project(":amt-lib:testing"))
    implementation(project(":amt-felles:ktor"))

    implementation(libs.testcontainers.postgresql)
    implementation(libs.testcontainers.kafka)

    constraints {
        implementation(libs.netty.codec.http2) {
            because("CVE-2026-33870")
        }
    }
    implementation(platform(libs.ktor.bom))

    implementation(project(":amt-lib:utils"))

//    implementation(libs.ktor.client.core)
//    implementation(libs.ktor.server.core)

    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.mock)
    implementation(libs.ktor.serialization.jackson)
    implementation(libs.kotlinx.coroutines.test)
}
