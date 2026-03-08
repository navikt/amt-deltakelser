import java.net.URI

group = "no.nav.amt.deltakelser.lib"

val libsWrapper = VersionCatalogWrapper.fromProject(project)

repositories {
    mavenCentral()
}

plugins {
    `java-library`
    `maven-publish`
    kotlin
    id("org.jlleitschuh.gradle.ktlint")
}

java {
    withJavadocJar()
    withSourcesJar()

    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

ktlint {
    version = libsWrapper.getVersion("ktlint.cli.version")
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
