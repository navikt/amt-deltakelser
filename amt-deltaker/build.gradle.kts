import org.gradle.api.tasks.testing.Test

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

val parallelTestClassNames = listOf(
    "no.nav.amt.deltaker.api.ExternalApiTest",
    "no.nav.amt.deltaker.api.DeltakerResponseBuilderTest",
    "no.nav.amt.deltaker.api.EnkeltplassApiTest",
    "no.nav.amt.deltaker.api.GjennomforingApiTest",
    "no.nav.amt.deltaker.api.InputvalideringTest",
    "no.nav.amt.deltaker.api.KladdApiTest",
    "no.nav.amt.deltaker.api.PameldingApiTest",
    "no.nav.amt.deltaker.api.SharedResponseMappersTest",
    "no.nav.amt.deltaker.api.TiltakskoordinatorApiTest",
    "no.nav.amt.deltaker.api.TiltakskoordinatorResponseBuilderTest",
    "no.nav.amt.deltaker.api.UlestHendelseApiTest",
    "no.nav.amt.deltaker.api.VeilederApiTest",
    "no.nav.amt.deltaker.application.plugins.AuthenticationTest",
    "no.nav.amt.deltaker.application.plugins.HealthTest",
    "no.nav.amt.deltaker.application.plugins.OpprettKladdRequestValidatorTest",
    "no.nav.amt.deltaker.application.plugins.SerializationTest",
    "no.nav.amt.deltaker.auth.TilgangskontrollServiceTest",
    "no.nav.amt.deltaker.clients.oppfolgingstilfelle.IsOppfolgingstilfelleClientTest",
    "no.nav.amt.deltaker.deltakerliste.kafka.GjennomforingV2KafkaPayloadExtensionsTest",
    "no.nav.amt.deltaker.digitalbruker.DigitalBrukerServiceTest",
    "no.nav.amt.deltaker.enkeltplass.EnkeltplassServiceTest",
    "no.nav.amt.deltaker.enkeltplass.GjennomforingUpserterTest",
    "no.nav.amt.deltaker.enkeltplass.OpplaringKategoriseringResponseExtensionsTest",
    "no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestPayloadTest",
    "no.nav.amt.deltaker.enkeltplass.kafka.TotrinnskontrollConsumerTest",
    "no.nav.amt.deltaker.enkeltplass.kafka.TotrinnskontrollHendelsePayloadTest",
    "no.nav.amt.deltaker.external.data.GjennomforingResponseTest",
    "no.nav.amt.deltaker.job.DeltakerProgresjonTest",
    "no.nav.amt.deltaker.kafka.GjennomforingConsumerTest",
    "no.nav.amt.deltaker.model.DeltakerlisteTest",
    "no.nav.amt.deltaker.navtiltakskoordinator.ulestdeltakerhendelse.DeltakerEndringHendelseConsumerTest",
    "no.nav.amt.deltaker.veileder.DeltakerLaaseServiceTest",
    "no.nav.amt.deltaker.veileder.InnsokServiceTest",
    "no.nav.amt.internapi.hendelse.HendelseDeltakerTest",
    "no.nav.amt.deltaker.veileder.endring.extensions.*",
)

tasks.named<Test>("test") {
    // New tests stay serial until they have been explicitly verified as isolated.
    filter {
        parallelTestClassNames.forEach(::excludeTestsMatching)
    }
}

val parallelTest = tasks.register<Test>("parallelTest") {
    description = "Runs explicitly isolated tests in parallel JVM processes."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    jvmArgs(
        "-Xshare:off",
        "-XX:+EnableDynamicAgentLoading",
        "--sun-misc-unsafe-memory-access=allow",
    )
    filter {
        parallelTestClassNames.forEach(::includeTestsMatching)
    }
    // MockK object mocks are process-global, so each class gets its own JVM.
    maxParallelForks = 2
    forkEvery = 1
}

tasks.named("check") {
    dependsOn(parallelTest)
}
