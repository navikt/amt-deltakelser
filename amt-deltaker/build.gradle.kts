plugins {
    id("amt-ktor-conventions")
}

dependencies {

    // --- POAO ---
    implementation(libs.poao.tilgang.client)

    // --- Feature Toggle ---
    implementation(libs.unleash)
}

application { mainClass.set("no.nav.amt.deltaker.ApplicationKt") }
