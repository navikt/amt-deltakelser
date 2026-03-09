plugins {
    id("amt-lib.conventions")
}

dependencies {
    api(libs.kafka.clients)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.logback.classic)
    implementation(project(":amt-lib:utils"))

    testImplementation(project(":amt-lib:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
}
