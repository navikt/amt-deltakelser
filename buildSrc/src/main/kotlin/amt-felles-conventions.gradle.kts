val libsWrapper = VersionCatalogWrapper.fromProject(project)

repositories {
    mavenCentral()
}

plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

kotlin {
    jvmToolchain(25)
}

ktlint {
    version = libsWrapper.getVersion("ktlint.cli.version")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs(
        "-Xshare:off",
        "-XX:+EnableDynamicAgentLoading",
        "-Dkotest.framework.classpath.scanning.autoscan.disable=true",
    )
}
