plugins {
    id("amt-lib.conventions")
}

dependencies {
    api(libs.tools.jackson.module.kotlin)

    testImplementation(project(":amt-lib:testing"))
}
