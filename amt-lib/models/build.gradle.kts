plugins {
    id("amt-lib.conventions")
}

dependencies {
    implementation(libs.jackson.datatype.jsr310)

    testImplementation(project(":amt-lib:testing"))
    testImplementation(libs.jackson.module.kotlin)
}
