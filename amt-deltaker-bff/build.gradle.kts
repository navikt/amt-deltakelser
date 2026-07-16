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

    // --- Test ---
    testImplementation(project(":amt-felles:typegenerering"))
    testImplementation(project(":amt-felles:archunit-test"))
}

application { mainClass = "no.nav.amt.deltaker.bff.ApplicationKt" }
