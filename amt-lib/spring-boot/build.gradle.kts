plugins {
    id("amt-lib.conventions")
}

dependencies {
    implementation(
        platform(
            "org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.version.get()}",
        ),
    )
    api("org.springframework:spring-web")
    api("org.springframework.security:spring-security-core")
    api("org.springframework.security:spring-security-web")
    api("jakarta.servlet:jakarta.servlet-api")

    testImplementation("org.springframework:spring-test")
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotest.assertions.core)
}
