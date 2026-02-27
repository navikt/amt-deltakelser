import java.net.URI

group = "no.nav.amt.deltakelser"

plugins {
    `java-library`
    `maven-publish`
    kotlin
}

repositories {
    mavenCentral()
}

java {
    withJavadocJar()
    withSourcesJar()

    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
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
