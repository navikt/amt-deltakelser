import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
}

ktlint {
    version = versionCatalog.findVersion("ktlint.cli.version").get().requiredVersion
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs(
        "-Xshare:off",
        "-XX:+EnableDynamicAgentLoading",
        "-Dkotest.framework.classpath.scanning.autoscan.disable=true",
    )
    // Lar Testcontainers gjenbruke containere på tvers av Gradle-runs.
    // Testcontainers leser denne env-varen direkte (system property funker IKKE).
    environment("TESTCONTAINERS_REUSE_ENABLE", "true")
    // Modulspesifikk reuse-label så parallelle test-JVM-er (org.gradle.parallel=true) ikke
    // deler én Kafka-container — shutdown-hook i SingletonKafkaProvider sletter topics globalt
    // og vil ellers kunne rydde for tester som fortsatt kjører i en annen modul.
    environment("TESTCONTAINERS_REUSE_LABEL_SUFFIX", project.path)
}
