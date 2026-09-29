plugins {
    id("amt-lib.conventions")
}

dependencies {
    implementation(project(":amt-lib:models"))

    api(libs.logback.classic)

    api(libs.tools.jackson.module.kotlin)

    api(libs.postgresql)
    api(libs.jdbi.core)
    api(libs.jdbi.sqlobject)
    api(libs.jdbi.kotlin)

    implementation(libs.kotlinx.coroutines.core)

    constraints {
        implementation(libs.okhttp) {
            because("CVE-2023-3635: upgrade OkHttp to fix vulnerability")
        }
    }
    implementation(libs.unleash)

    testImplementation(project(":amt-lib:testing"))
}
