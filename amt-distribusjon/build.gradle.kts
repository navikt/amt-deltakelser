plugins {
    id("amt-ktor-conventions")
}

dependencies {
    // --- Varsel ---
    implementation(libs.tms.varsel.kotlin.builder)
    implementation(project(":amt-felles:visningsnavn"))
}

application { mainClass = "no.nav.amt.distribusjon.ApplicationKt" }
