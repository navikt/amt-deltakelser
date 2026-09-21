plugins {
    id("amt-spring-conventions")
}

dependencies {
    implementation(project(":amt-felles:visningsnavn"))
    implementation(project(":amt-felles:intern-api-kontrakter"))

    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation(libs.nav.common.log)
}
