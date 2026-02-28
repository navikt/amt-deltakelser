
plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.jvm)
    implementation(libs.ktor.plugin)
    implementation(libs.ktlint.gradle.plugin)
}
