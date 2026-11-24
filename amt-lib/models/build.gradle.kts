plugins {
    id("amt-lib.conventions")
}

dependencies {
    api(libs.tools.jackson.databind)
    runtimeOnly(libs.tools.jackson.module.kotlin)

    testImplementation(project(":amt-lib:testing"))
    testImplementation(libs.tools.jackson.module.kotlin)
}
