repositories {
    mavenCentral()
}

plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

dependencies {
    api(project(":amt-lib:lib:testing"))
    implementation(project(":amt-felles:ktor"))

    implementation(libs.testcontainers.postgresql)
}

kotlin {
    jvmToolchain(25)
}

ktlint {
    version = libs.versions.ktlint.cli.version
}
