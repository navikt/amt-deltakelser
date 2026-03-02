plugins {
    id("amt-ktor-conventions")
}

dependencies {

    // --- Audit logging ---
    implementation(libs.nav.common.audit.log)

    // --- POAO ---
    implementation(libs.poao.tilgang.client)

    // --- Feature Toggle ---
    implementation(libs.unleash)
}

application { mainClass = "no.nav.amt.deltaker.bff.ApplicationKt" }
