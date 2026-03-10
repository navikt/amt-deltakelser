plugins {
    id("amt-ktor-conventions")
}

dependencies {

    implementation(project(":amt-felles:bff-deltaker-kontrakt"))

    // --- POAO ---
    implementation(libs.poao.tilgang.client)

    // --- Feature Toggle ---
    implementation(libs.unleash)
}

application { mainClass = "no.nav.amt.deltaker.ApplicationKt" }
