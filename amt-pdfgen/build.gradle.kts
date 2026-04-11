plugins {
    kotlin("jvm") version "2.3.0"
}

val jsoupVersion = "1.22.1"
val pdfgenCoreVersion = "1.1.67"
val kotestVersion = "6.0.7"
val mockkVersion = "1.14.7"
val jacksonModuleKotlinVersion = "2.20.1"
val amtLibVersion = "1.2026.02.08_16.17-c69b2d0f7d8d"

repositories {
    mavenCentral()
    maven { setUrl("https://github-package-registry-mirror.gc.nav.no/cached/maven-release") }
}

dependencies {
    testImplementation("no.nav.amt.lib:models:$amtLibVersion")
    testImplementation("org.jsoup:jsoup:$jsoupVersion")
    testImplementation("no.nav.pdfgen:pdfgen-core:$pdfgenCoreVersion")
    testImplementation("io.kotest:kotest-runner-junit5-jvm:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-table-jvm:$kotestVersion")
    testImplementation("io.mockk:mockk-jvm:$mockkVersion")
    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonModuleKotlinVersion")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonModuleKotlinVersion")
}

tasks.test {
    useJUnitPlatform()
}
