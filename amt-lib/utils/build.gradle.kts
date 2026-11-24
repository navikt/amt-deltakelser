plugins {
    id("amt-lib.conventions")
}

dependencies {
    api(project(":amt-lib:models"))

    implementation(libs.slf4j.api)

    api(libs.tools.jackson.module.kotlin)
    api(libs.postgresql)

    implementation(libs.kotlinx.coroutines.core)

    constraints {
        implementation(libs.okhttp) {
            because("CVE-2023-3635: upgrade OkHttp to fix vulnerability")
        }
    }
    api(libs.unleash)

    testImplementation(project(":amt-lib:testing"))
}
