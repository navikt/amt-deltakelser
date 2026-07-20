plugins {
    id("amt-felles-conventions")
}

dependencies {
    api(project(":amt-lib:models"))

    testImplementation(project(":amt-lib:testing"))
}
