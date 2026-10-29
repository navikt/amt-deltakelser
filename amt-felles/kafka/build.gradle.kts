plugins {
    id("amt-felles-conventions")
}

dependencies {
    constraints {
        api(libs.snappy.java) {
            because("Fixes CVE-2026-93451 and CVE-2026-108106")
        }
    }

    api(libs.kafka.clients)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.logback.classic)
    implementation(project(":amt-lib:utils"))

    testImplementation(project(":amt-felles:ktor-test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
