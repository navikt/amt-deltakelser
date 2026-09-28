plugins {
    id("amt-ktor-conventions")
}

dependencies {
    // --- Varsel ---
    implementation(libs.tms.varsel.kotlin.builder)

    // --- Visningsnavn ---
    implementation(project(":amt-felles:visningsnavn"))

    implementation("org.jdbi:jdbi3-core:3.54.0")
    implementation("org.jdbi:jdbi3-sqlobject:3.54.0")
}

application { mainClass = "no.nav.amt.distribusjon.ApplicationKt" }
