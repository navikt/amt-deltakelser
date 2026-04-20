plugins {
    id("amt-lib.conventions")
}

dependencies {
    implementation(project(":amt-lib:models"))

    api(libs.logback.classic)

    api(libs.jackson.module.kotlin)
    api(libs.jackson.datatype.jsr310)

    implementation(libs.kotlinx.coroutines.core)

    constraints {
        implementation(libs.okhttp) {
            because("CVE-2023-3635: upgrade OkHttp to fix vulnerability")
        }
    }
    implementation(libs.unleash)

    testImplementation(project(":amt-lib:testing"))
}
