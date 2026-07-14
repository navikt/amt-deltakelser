plugins {
    id("amt-felles-conventions")
}

dependencies {
    api(project(":amt-lib:models"))
    implementation(project(":amt-lib:utils"))
    testImplementation(project(":amt-lib:testing"))
}
