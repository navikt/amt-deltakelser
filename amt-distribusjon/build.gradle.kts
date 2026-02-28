plugins {
    id("amt-ktor-conventions")
}

dependencies {

    // --- Varsel ---
    implementation(libs.tms.varsel.kotlin.builder)
}

application { mainClass.set("no.nav.amt.distribusjon.ApplicationKt") }
