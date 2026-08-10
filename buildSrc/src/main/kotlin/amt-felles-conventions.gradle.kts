plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

val libsWrapper = VersionCatalogWrapper.fromProject(project)

repositories {
    mavenCentral()
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
    // Lar Testcontainers gjenbruke containere på tvers av Gradle-runs.
    // Testcontainers leser denne env-varen direkte (system property funker IKKE).
    environment("TESTCONTAINERS_REUSE_ENABLE", "true")
    // Modulspesifikk reuse-label så parallelle test-JVM-er (org.gradle.parallel=true) ikke
    // deler én Kafka-container — shutdown-hook i SingletonKafkaProvider sletter topics globalt
    // og vil ellers kunne rydde for tester som fortsatt kjører i en annen modul.
    environment("TESTCONTAINERS_REUSE_LABEL_SUFFIX", project.path)
}
