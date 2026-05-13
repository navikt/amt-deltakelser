plugins {
    kotlin("jvm")
}

group = "no.nav.amt.mocks"
version = "0.0.0"

repositories {
    mavenCentral()
    maven(
        url = "https://github-package-registry-mirror.gc.nav.no/cached/maven-release"
    ) {
        credentials {
            username = System.getenv("GITHUB_USERNAME")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("no.nav.poao-tilgang:poao-tilgang-test-core:4.2026.05.11_07.01-54ab6eae4dde")
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.test {
    useJUnitPlatform()
}