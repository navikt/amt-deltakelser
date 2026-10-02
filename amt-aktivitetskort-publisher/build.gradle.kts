plugins {
    id("amt-spring-conventions")
}

dependencies {
    implementation(project(":amt-felles:visningsnavn"))
    implementation(project(":amt-felles:intern-api-kontrakter"))
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation(libs.nav.common.kafka)
    implementation(libs.shedlock.spring)
    implementation(libs.shedlock.jdbc.template)
}
