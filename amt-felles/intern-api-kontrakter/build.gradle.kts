plugins {
    id("amt-felles-conventions")
}

dependencies {
    implementation(project(":amt-lib:models"))
    implementation(project(":amt-lib:utils"))
    testImplementation(project(":amt-lib:testing"))
}
