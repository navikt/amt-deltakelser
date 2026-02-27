plugins {
    id("amt-lib.conventions")
}

dependencies {
    implementation(libs.logback.classic)

    implementation(project(":amt-lib:lib:utils"))
    api(project(":amt-lib:lib:kafka"))
    implementation(libs.jackson.module.kotlin)

    api(libs.micrometer.prometheus)
    implementation(libs.prometheus.metrics.instrumentation)
    implementation(libs.prometheus.metrics.exporter)

    implementation(libs.kotliquery)
    implementation(libs.postgresql)

    testImplementation(project(":amt-lib:lib:testing"))
}
