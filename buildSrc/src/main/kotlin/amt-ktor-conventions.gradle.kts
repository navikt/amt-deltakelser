val libsWrapper = VersionCatalogWrapper.fromProject(project)

repositories {
    mavenCentral()
    maven { setUrl("https://github-package-registry-mirror.gc.nav.no/cached/maven-release") }
}

plugins {
    kotlin("jvm")
    application
    id("org.jlleitschuh.gradle.ktlint")
}

dependencies {
    // --- Ktor ---
    implementation(platform(libsWrapper.getLibrary("ktor.bom")))
    libsWrapper.getBundle("ktor.server").forEach { implementation(it) }
    libsWrapper.getBundle("ktor.client").forEach { implementation(it) }

    // --- Logging ---
    libsWrapper.getBundle("logging").forEach { implementation(it) }

    // --- amt-felles, amt-felles:ktor drar inn database, models og utils
    implementation(project(":amt-felles:ktor"))

    // --- Test ---
    testImplementation(project(":amt-felles:ktor-test"))
    libsWrapper.getBundle("ktor.test").forEach { testImplementation(it) }
    testImplementation(libsWrapper.getLibrary("nimbus.jose.jwt"))
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xwarning-level=IDENTITY_SENSITIVE_OPERATIONS_WITH_VALUE_TYPE:disabled",
        )
    }
}

ktlint {
    version = libsWrapper.getVersion("ktlint.cli.version")
}

application {
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf(
        "-Dio.ktor.development=$isDevelopment",
        "-Xshare:off",
    )
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs(
        "-Xshare:off",
        "-XX:+EnableDynamicAgentLoading",
    )
}
