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
    constraints {
        implementation(libsWrapper.getLibrary("tools.jackson.core")) {
            because("Misc Dependabot alerts")
        }

        implementation(libsWrapper.getLibrary("jackson.core")) {
            because("GHSA-72hv-8253-57qq")
        }
    }

    // Netty BOM — holder alle io.netty-moduler på samme versjon og fikser CVE-er samlet
    implementation(platform(libsWrapper.getLibrary("netty.bom")))

    // --- Ktor ---
    implementation(platform(libsWrapper.getLibrary("ktor.bom")))
    libsWrapper.getBundle("ktor.server").forEach { implementation(it) }
    libsWrapper.getBundle("ktor.client").forEach { implementation(it) }

    // --- Logging ---
    libsWrapper.getBundle("logging").forEach { implementation(it) }

    // --- amt-felles, amt-felles:ktor drar inn database, models og utils
    implementation(project(":amt-felles:ktor"))

    implementation(project(":amt-felles:intern-api-kontrakter"))

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
        "--sun-misc-unsafe-memory-access=allow",
    )
    // Lar Testcontainers gjenbruke containere på tvers av Gradle-runs.
    // Testcontainers leser denne env-varen direkte (system property funker IKKE).
    environment("TESTCONTAINERS_REUSE_ENABLE", "true")
}
