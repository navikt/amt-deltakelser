plugins {
    id("amt-spring-conventions")
}

dependencies {
    implementation(project(":amt-felles:visningsnavn"))
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation(libs.nav.common.log)
}
