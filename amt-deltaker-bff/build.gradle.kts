plugins {
    id("amt-ktor-conventions")
}

dependencies {
    testImplementation(project(":amt-lib:testing"))

    // --- Audit logging ---
    implementation(libs.nav.common.audit.log)

    // --- POAO ---
    implementation(libs.poao.tilgang.client)

    // --- Feature Toggle ---
    implementation(libs.unleash)

    // --- Test ---
    testImplementation(libs.archunit.junit5)
}

application { mainClass = "no.nav.amt.deltaker.bff.ApplicationKt" }
