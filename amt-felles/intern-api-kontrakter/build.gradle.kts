plugins {
    id("amt-felles-conventions")
}

dependencies {
    implementation(project(":amt-lib:models"))
    testImplementation(project(":amt-lib:testing"))
}
