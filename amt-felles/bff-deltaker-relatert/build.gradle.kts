repositories {
    mavenCentral()
}

plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

dependencies {
    implementation(project(":amt-lib:models"))
}

kotlin {
    jvmToolchain(25)
}

ktlint {
    version = libs.versions.ktlint.cli.version
}
