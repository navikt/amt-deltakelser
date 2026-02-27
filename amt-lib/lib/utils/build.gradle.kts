plugins {
    id("amt-lib.conventions")
}

dependencies {
    implementation(project(":amt-lib:lib:models"))

    api(libs.logback.classic)

    implementation(libs.bundles.database)

    api(libs.jackson.module.kotlin)
    api(libs.jackson.datatype.jsr310)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.unleash)

    testImplementation(project(":amt-lib:lib:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
}
