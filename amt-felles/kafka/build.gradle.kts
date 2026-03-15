plugins {
    id("amt-felles-conventions")
}

dependencies {
    api(libs.kafka.clients)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.logback.classic)
    implementation(project(":amt-lib:utils"))

    testImplementation(project(":amt-felles:ktor-test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
