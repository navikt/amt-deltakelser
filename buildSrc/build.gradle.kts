
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
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.spring.dependency.management.gradle.plugin)
    implementation(libs.kotlin.spring.gradle.plugin)
}
