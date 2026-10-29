import java.net.URI
import org.gradle.api.artifacts.VersionCatalogsExtension

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

java {
    withJavadocJar()
    withSourcesJar()

    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
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
