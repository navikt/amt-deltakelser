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

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotest.assertions.core)
}
