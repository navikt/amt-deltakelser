package no.nav.amt.deltaker.bff.commonresponse

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.model.ArrangorModel
import no.nav.amt.deltaker.bff.utils.TestData.lagGjennomforingModel
import no.nav.amt.deltaker.bff.veileder.api.response.OpplaringKategoriseringValgResponse
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class DeltakerlisteResponseTest {
    @Test
    fun `fromModel - gruppe uten kategorisering - mapper korrekt`() {
        val model = lagGjennomforingModel(
            type = GjennomforingType.Gruppe,
            status = GjennomforingStatusType.GJENNOMFORES,
            oppstart = Oppstartstype.LOPENDE,
            startDato = LocalDate.of(2026, 1, 1),
            sluttDato = LocalDate.of(2027, 1, 1),
            oppmoteSted = "Nav Grünerløkka",
            pameldingstype = GjennomforingPameldingType.DIREKTE_VEDTAK,
        )

        val response = DeltakerlisteResponse(model)

        response.deltakerlisteId shouldBe model.id
        response.deltakerlisteNavn shouldBe model.navn
        response.tiltakskode shouldBe TiltakskodeResponse(model.tiltak.tiltakskode)
        response.oppstartstype shouldBe Oppstartstype.LOPENDE
        response.startdato shouldBe LocalDate.of(2026, 1, 1)
        response.sluttdato shouldBe LocalDate.of(2027, 1, 1)
        response.status shouldBe GjennomforingStatusType.GJENNOMFORES
        response.erEnkeltplass shouldBe false
        response.oppmoteSted shouldBe "Nav Grünerløkka"
        response.pameldingstype shouldBe GjennomforingPameldingType.DIREKTE_VEDTAK
        response.opplaringKategoriseringValg shouldBe null
        response.prisinformasjon shouldBe null
    }

    @Test
    fun `fromModel - enkeltplass med kategorisering - mapper opplaringKategoriseringValg`() {
        val bransjeId = UUID.randomUUID()
        val forerkortId = UUID.randomUUID()
        val kategorisering = OpplaringKategoriseringValg(
            valgteKategoriseringer = setOf(
                OpplaringKategoriseringValg.ValgteFelt(
                    representerer = OpplaringKategoriseringType.BRANSJE_ID,
                    valg = mapOf(bransjeId to "Bygg og anlegg"),
                ),
                OpplaringKategoriseringValg.ValgteFelt(
                    representerer = OpplaringKategoriseringType.FORERKORT,
                    valg = mapOf(forerkortId to "B"),
                ),
            ),
            valgteSertifiseringer = setOf(
                SertifiseringValg(id = 1, navn = "Truckfører T1"),
            ),
        )

        val model = lagGjennomforingModel(
            type = GjennomforingType.Enkeltplass,
        ).copy(opplaringKategoriseringValg = kategorisering)

        val response = DeltakerlisteResponse(model)

        response.erEnkeltplass shouldBe true
        response.opplaringKategoriseringValg shouldBe OpplaringKategoriseringValgResponse(
            valgteKategoriseringer = setOf(
                OpplaringKategoriseringValgResponse.Kategorisering(
                    type = OpplaringKategoriseringType.BRANSJE_ID,
                    valgteElementer = listOf(
                        OpplaringKategoriseringValgResponse.Valg(id = bransjeId, visningsnavn = "Bygg og anlegg"),
                    ),
                ),
                OpplaringKategoriseringValgResponse.Kategorisering(
                    type = OpplaringKategoriseringType.FORERKORT,
                    valgteElementer = listOf(
                        OpplaringKategoriseringValgResponse.Valg(id = forerkortId, visningsnavn = "B"),
                    ),
                ),
            ),
            valgteSertifiseringer = setOf(
                OpplaringKategoriseringValgResponse.SertifiseringValgResponse(id = 1, navn = "Truckfører T1"),
            ),
        )
    }

    @Test
    fun `fromModel - enkeltplass uten kategorisering - returnerer null for begge felter`() {
        val model = lagGjennomforingModel(
            type = GjennomforingType.Enkeltplass,
        ).copy(opplaringKategoriseringValg = null)

        val response = DeltakerlisteResponse(model)

        response.erEnkeltplass shouldBe true
        response.opplaringKategoriseringValg shouldBe null
    }

    @Test
    fun `fromModel - arrangor er null - setter arrangorNavn til Ukjent arrangør`() {
        val model = lagGjennomforingModel(arrangor = null)

        val response = DeltakerlisteResponse(model)

        response.arrangorNavn shouldBe "Ukjent arrangør"
        response.arrangor shouldBe null
    }

    @Test
    fun `fromModel - har arrangor - mapper arrangor korrekt`() {
        val model = lagGjennomforingModel(
            arrangor = ArrangorModel(navn = "Test Arrangør AS", organisasjonsnummer = "987654321"),
        )

        val response = DeltakerlisteResponse(model)

        response.arrangorNavn shouldBe "Test Arrangør AS"
        response.arrangor shouldBe ArrangorResponse(
            navn = "Test Arrangør AS",
            organisasjonsnummer = "987654321",
        )
    }

    @Test
    fun `fromModel - pameldingstype er null - bruker TRENGER_GODKJENNING som default`() {
        val model = lagGjennomforingModel(pameldingstype = null)

        val response = DeltakerlisteResponse(model)

        response.pameldingstype shouldBe GjennomforingPameldingType.TRENGER_GODKJENNING
    }
}
