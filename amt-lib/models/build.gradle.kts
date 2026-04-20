plugins {
    id("amt-lib.conventions")
}

dependencies {
    api(libs.jackson.datatype.jsr310)

    testImplementation(project(":amt-lib:testing"))
    testImplementation(libs.jackson.module.kotlin)
}
