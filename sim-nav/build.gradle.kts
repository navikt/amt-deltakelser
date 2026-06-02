plugins {
    kotlin("jvm")
    id("application")
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
    implementation(project(":amt-lib:models"))
    implementation(platform(libs.ktor.bom))
    implementation("com.graphql-java:graphql-java:22.4")
    implementation("com.graphql-java:graphql-java-extended-scalars:22.0")
    implementation(libs.ktor.server.core)
    implementation("io.ktor:ktor-server-html-builder-jvm")
    implementation(libs.ktor.server.netty)
    implementation(libs.kafka.clients)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.mock.oauth2.server)

    // Database
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation("org.jetbrains.exposed:exposed-core:0.43.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.43.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.43.0")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

application {
    mainClass.set("MainKt")
}

tasks.test {
    useJUnitPlatform()
}

