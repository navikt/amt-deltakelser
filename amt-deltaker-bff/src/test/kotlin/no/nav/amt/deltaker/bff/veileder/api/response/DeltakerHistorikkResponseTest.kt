package no.nav.amt.deltaker.bff.veileder.api.response

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.amt.deltaker.bff.commonresponse.PrisinformasjonResponse
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerEndring
import no.nav.amt.deltaker.bff.utils.TestData.lagEnkeltplassHistorikkMedVedtak
import no.nav.amt.deltaker.bff.utils.TestData.lagNavAnsatteForHistorikk
import no.nav.amt.deltaker.bff.utils.TestData.lagNavEnheterForHistorikk
import no.nav.amt.internapi.deltaker.response.DeltakelsesinnholdResponse
import no.nav.amt.internapi.deltaker.response.OpplaringKategoriseringValgResponse
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.OkonomiGodkjentForHistorikk
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.writePolymorphicCollectionAsString
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.readValue
import java.util.UUID

class DeltakerHistorikkResponseTest {
    private val historikk = lagEnkeltplassHistorikkMedVedtak()
    private val godkjenning = historikk
        .filterIsInstance<DeltakerHistorikk.EnkeltplassOkonomiGodkjent>()
        .single { it.data.erForsteGodkjenning }
        .data
    private val vedtak = godkjenning.vedtak.shouldNotBeNull()

    @Test
    fun `opplæring godkjent viser opprinnelige vedtaksdetaljer og pris etter senere endringer`() {
        // For enkeltplasser vises vedtaket under «Opplæring godkjent», ikke som et eget historikkinnslag.
        // Detaljene og prisen må være de som gjaldt da vedtaket ble fattet, selv om deltakeren eller prisen endres senere.
        val response = tilResponse(historikk + DeltakerHistorikk.Endring(lagDeltakerEndring()))
        val godkjenninger = response.filterIsInstance<EnkeltplassOkonomiGodkjentResponse>()
        val forste = godkjenninger.single { it.erForsteGodkjenning }
        val senere = godkjenninger.single { !it.erForsteGodkjenning }

        response.size shouldBe 3
        forste.prisinformasjon shouldBe PrisinformasjonResponse.Anskaffelse(20000, null)
        senere.prisinformasjon shouldBe PrisinformasjonResponse.Anskaffelse(35000, null)
        senere.vedtaksdetaljer shouldBe null
        assertSoftly(forste.vedtaksdetaljer.shouldNotBeNull()) {
            val deltaker = vedtak.deltakerVedVedtak
            fattet shouldBe vedtak.fattet
            startdato shouldBe deltaker.startdato
            sluttdato shouldBe deltaker.sluttdato
            dagerPerUke shouldBe deltaker.dagerPerUke
            deltakelsesprosent shouldBe deltaker.deltakelsesprosent
            bakgrunnsinformasjon shouldBe "Bakgrunn ved vedtak"
            deltakelsesinnhold shouldBe DeltakelsesinnholdResponse(deltaker.deltakelsesinnhold.shouldNotBeNull())
            opplaringKategorisering shouldBe OpplaringKategoriseringValgResponse(deltaker.opplaringKategorisering.shouldNotBeNull())
        }
    }

    @Test
    fun `vedtaksdetaljer kan mappes uten separat vedtak og med en annen godkjenner`() {
        val historikk = listOf(
            DeltakerHistorikk.EnkeltplassOkonomiGodkjent(
                godkjenning.copy(
                    sistEndretAvNavAnsattId = UUID.randomUUID(),
                    sistEndretAvNavEnhetId = UUID.randomUUID(),
                ),
            ),
        )

        val response = tilResponse(historikk).single().shouldBeInstanceOf<EnkeltplassOkonomiGodkjentResponse>()

        response.vedtaksdetaljer.shouldNotBeNull().fattet shouldBe vedtak.fattet
    }

    @Test
    fun `manglende eller ikke fattet vedtak erstattes ikke med et annet historikkinnslag`() {
        listOf(null, vedtak.copy(fattet = null)).forEach {
            tilGodkjenningResponse(godkjenning.copy(vedtak = it)).vedtaksdetaljer shouldBe null
        }
    }

    @Test
    fun `manglende prisinformasjon erstattes ikke med senere godkjent pris`() {
        val response = tilGodkjenningResponse(godkjenning.copy(prisinformasjon = null))

        response.prisinformasjon shouldBe null
        response.vedtaksdetaljer.shouldNotBeNull().fattet shouldBe vedtak.fattet
    }

    @Test
    fun `valgfrie tomme felt i vedtaket forblir tomme`() {
        val tomtVedtak = vedtak.copy(
            deltakerVedVedtak = vedtak.deltakerVedVedtak.copy(
                startdato = null,
                sluttdato = null,
                deltakelsesinnhold = null,
                opplaringKategorisering = null,
                dagerPerUke = null,
                deltakelsesprosent = null,
                bakgrunnsinformasjon = null,
            ),
        )
        val detaljer = tilGodkjenningResponse(godkjenning.copy(vedtak = tomtVedtak)).vedtaksdetaljer.shouldNotBeNull()

        assertSoftly(detaljer) {
            fattet shouldBe vedtak.fattet
            startdato shouldBe null
            sluttdato shouldBe null
            deltakelsesinnhold shouldBe null
            opplaringKategorisering shouldBe null
            dagerPerUke shouldBe null
            deltakelsesprosent shouldBe null
            bakgrunnsinformasjon shouldBe null
        }
    }

    @Test
    fun `godkjenning med lagret vedtak kan serialiseres og leses tilbake`() {
        objectMapper.readValue<OkonomiGodkjentForHistorikk>(objectMapper.writeValueAsString(godkjenning)) shouldBe godkjenning
    }

    @Test
    fun `økonomihistorikk uten lagret vedtak kan leses tilbake`() {
        val json =
            """
            {
                "sistEndret": "2026-10-15T12:00:00",
                "sistEndretAvNavAnsattId": "64ca7ba0-279b-435e-b1a3-4f4f6038d431",
                "sistEndretAvNavEnhetId": "9b696c67-ec0f-41b8-a61e-015323b401af",
                "erForsteGodkjenning": true,
                "prisinformasjon": {"type": "Anskaffelse", "pris": 20000}
            }
            """.trimIndent()

        val godkjenning = objectMapper.readValue<OkonomiGodkjentForHistorikk>(json)

        godkjenning.vedtak shouldBe null
        godkjenning.erForsteGodkjenning shouldBe true
        godkjenning.prisinformasjon shouldBe PrisinformasjonDto.Anskaffelse(pris = 20000)
    }

    @Test
    fun `direkte og nøstede vedtak bruker samme respons gjennom JSON`() {
        // I testdataene finnes vedtaket både som eget historikkinnslag («direkte»)
        // og inne i økonomigodkjenningen («nøstet»).
        // Begge skal beholde de samme detaljene når responsen skrives til JSON og leses tilbake.
        val response = tilResponse(pameldingstype = GjennomforingPameldingType.DIREKTE_VEDTAK)
        val json = objectMapper.writePolymorphicCollectionAsString(response)
        val deserialisert = objectMapper.readValue<List<DeltakerHistorikkResponse>>(json)

        deserialisert shouldBe response
        val forste = deserialisert.filterIsInstance<EnkeltplassOkonomiGodkjentResponse>().single { it.erForsteGodkjenning }
        forste.vedtaksdetaljer shouldBe deserialisert.filterIsInstance<VedtakResponse>().single()
    }

    private fun tilGodkjenningResponse(data: OkonomiGodkjentForHistorikk) = tilResponse(
        historikk.map {
            if (it is DeltakerHistorikk.EnkeltplassOkonomiGodkjent && it.data.erForsteGodkjenning) it.copy(data = data) else it
        },
    ).filterIsInstance<EnkeltplassOkonomiGodkjentResponse>().single { it.erForsteGodkjenning }

    private fun tilResponse(
        historikk: List<DeltakerHistorikk> = this.historikk,
        pameldingstype: GjennomforingPameldingType? = null,
    ) = DeltakerHistorikkResponse.fromModels(
        models = historikk,
        arrangornavn = "Historisk opplæringssted",
        oppstartstype = Oppstartstype.LOPENDE,
        pameldingstype = pameldingstype,
        enheter = lagNavEnheterForHistorikk(historikk).associateBy { it.id },
        ansatte = lagNavAnsatteForHistorikk(historikk).associateBy { it.id },
    )
}
