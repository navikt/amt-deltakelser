import java.net.URI

plugins {
    `java-library`
    `maven-publish`
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

group = "no.nav.amt.deltakelser.lib"

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

repositories {
    mavenCentral()
}

val javaVersion = versionCatalog.findVersion("java.version").get().requiredVersion

java {
    withJavadocJar()
    withSourcesJar()

    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
}

ktlint {
    version = versionCatalog.findVersion("ktlint.cli.version").get().requiredVersion
}

publishing {
    publications {
        create<MavenPublication>("amt-lib") {
            from(components["java"])
        }
    }

    repositories {
        maven {
            url = URI("https://maven.pkg.github.com/navikt/amt-deltakelser")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs(
        "-Xshare:off",
        "-XX:+EnableDynamicAgentLoading",
        "-Dkotest.framework.classpath.scanning.autoscan.disable=true",
    )
}
