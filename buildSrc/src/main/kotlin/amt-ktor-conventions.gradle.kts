import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    kotlin("jvm")
    application
    id("org.jlleitschuh.gradle.ktlint")
}

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

repositories {
    mavenCentral()
    maven { setUrl("https://github-package-registry-mirror.gc.nav.no/cached/maven-release") }
}

dependencies {
    constraints {
        implementation(versionCatalog.findLibrary("guava").get()) {
            because("Fixes Guava vulnerabilities")
        }

        implementation(versionCatalog.findLibrary("lz4.java").get()) {
            because("Fixes CVE-2026-59949")
        }

        implementation(versionCatalog.findLibrary("tools.jackson.core").get()) {
            because("Misc Dependabot alerts")
        }

        implementation(versionCatalog.findLibrary("jackson.core").get()) {
            because("GHSA-72hv-8253-57qq")
        }
    }

    // Netty BOM — holder alle io.netty-moduler på samme versjon og fikser CVE-er samlet
    implementation(platform(versionCatalog.findLibrary("netty.bom").get()))

    // --- Ktor ---
    implementation(platform(versionCatalog.findLibrary("ktor.bom").get()))
    implementation(versionCatalog.findBundle("ktor.server").get())
    implementation(versionCatalog.findBundle("ktor.client").get())

    // --- Logging ---
    implementation(versionCatalog.findBundle("logging").get())

    // --- amt-felles, amt-felles:ktor drar inn database, models og utils
    implementation(project(":amt-felles:ktor"))

    implementation(project(":amt-felles:intern-api-kontrakter"))

    // --- Test ---
    testImplementation(project(":amt-felles:ktor-test"))
    testImplementation(versionCatalog.findBundle("ktor.test").get())
    testImplementation(versionCatalog.findLibrary("nimbus.jose.jwt").get())
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
    version = versionCatalog.findVersion("ktlint.cli.version").get().requiredVersion
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
    // Modulspesifikk reuse-label så parallelle test-JVM-er (org.gradle.parallel=true) ikke
    // deler én container på tvers av moduler. Se ContainerReuseConfig for detaljer.
    environment("TESTCONTAINERS_REUSE_LABEL_SUFFIX", project.path)
}
