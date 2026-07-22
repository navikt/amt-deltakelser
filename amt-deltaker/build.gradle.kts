plugins {
    id("amt-ktor-conventions")
}

dependencies {
    // --- POAO ---
    implementation(libs.poao.tilgang.client)

    // --- Feature Toggle ---
    implementation(libs.unleash)

    // --- Visningsnavn ---
    implementation(project(":amt-felles:visningsnavn"))
}

application { mainClass = "no.nav.amt.deltaker.ApplicationKt" }
