plugins {
    id("amt-spring-conventions")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    implementation(libs.nav.common.audit.log)
    implementation(libs.nav.common.rest)

    implementation(libs.shedlock.spring)
    implementation(libs.shedlock.jdbc.template)

    implementation(project(":amt-felles:kafka"))
    testImplementation(project(":amt-felles:archunit-test"))
}
