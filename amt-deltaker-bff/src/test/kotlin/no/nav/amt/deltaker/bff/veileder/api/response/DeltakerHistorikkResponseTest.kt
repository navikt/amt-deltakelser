package no.nav.amt.deltaker.bff.veileder.api.response

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerEndring
import no.nav.amt.deltaker.bff.utils.TestData.lagEnkeltplassHistorikkMedVedtak
import no.nav.amt.deltaker.bff.utils.TestData.lagNavAnsatteForHistorikk
import no.nav.amt.deltaker.bff.utils.TestData.lagNavEnheterForHistorikk
import no.nav.amt.deltaker.bff.utils.TestData.lagVedtak
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
    @Test
    fun `første godkjenning viser vedtaksdetaljer selv om separat vedtak filtreres bort`() {
        val historikk = lagEnkeltplassHistorikkMedVedtak()
        val avbruttUtkast = DeltakerHistorikk.Vedtak(lagVedtak())

        val response = tilJson(listOf(avbruttUtkast) + historikk)

        response.size() shouldBe 2
        val senere = response[0]
        senere["type"].asText() shouldBe "EnkeltplassOkonomiGodkjent"
        senere["erForsteGodkjenning"].asBoolean() shouldBe false
        senere["vedtaksdetaljer"].isNull shouldBe true
        senere["prisinformasjon"]["pris"].asInt() shouldBe 35000

        val forste = response[1]
        forste["erForsteGodkjenning"].asBoolean() shouldBe true
        forste["prisinformasjon"]["pris"].asInt() shouldBe 20000
        val detaljer = forste["vedtaksdetaljer"]
        detaljer["type"].asText() shouldBe "Vedtak"
        detaljer["fattet"].asText() shouldBe "2026-10-15T12:00:00"
        detaljer["startdato"].asText() shouldBe "2026-11-01"
        detaljer["sluttdato"].asText() shouldBe "2027-06-30"
        detaljer["dagerPerUke"].asDouble() shouldBe 3.0
        detaljer["deltakelsesprosent"].asDouble() shouldBe 60.0
        detaljer["bakgrunnsinformasjon"].asText() shouldBe "Bakgrunn ved vedtak"
        detaljer["deltakelsesinnhold"]["ledetekst"].asText() shouldBe "Innhold ved vedtak"
        detaljer["deltakelsesinnhold"]["innhold"][0]["beskrivelse"].asText() shouldBe "Historisk opplæring"
        detaljer["opplaringKategorisering"]["valgteKategoriseringer"][0]["type"].asText() shouldBe "UTDANNINGSPROGRAM_ID"
        detaljer["opplaringKategorisering"]["valgteKategoriseringer"][0]["valgteElementer"][0]["visningsnavn"]
            .asText() shouldBe "Historisk utdanningsprogram"
    }

    @Test
    fun `vedtaksdetaljer kommer fra godkjenningen uten eget vedtaksinnslag`() {
        val historikk = lagEnkeltplassHistorikkMedVedtak()
            .filterIsInstance<DeltakerHistorikk.EnkeltplassOkonomiGodkjent>()
            .map {
                it.copy(
                    data = it.data.copy(
                        sistEndretAvNavAnsattId = UUID.randomUUID(),
                        sistEndretAvNavEnhetId = UUID.randomUUID(),
                    ),
                )
            }

        val response = tilJson(historikk)

        response[1]["vedtaksdetaljer"]["bakgrunnsinformasjon"].asText() shouldBe "Bakgrunn ved vedtak"
        response[1]["vedtaksdetaljer"]["opprettetAv"].isTextual shouldBe true
        response[1]["vedtaksdetaljer"]["opprettetAvEnhet"].isTextual shouldBe true
        response[1]["prisinformasjon"]["pris"].asInt() shouldBe 20000
    }

    @Test
    fun `manglende vedtak på godkjenningen erstattes ikke med et annet historikkinnslag`() {
        val historikk = lagEnkeltplassHistorikkMedVedtak().map {
            if (it is DeltakerHistorikk.EnkeltplassOkonomiGodkjent) {
                it.copy(data = it.data.copy(vedtak = null))
            } else {
                it
            }
        }

        tilJson(historikk)[1]["vedtaksdetaljer"].isNull shouldBe true
    }

    @Test
    fun `ikke fattet vedtak på godkjenningen brukes ikke som vedtaksgrunnlag`() {
        val historikk = lagEnkeltplassHistorikkMedVedtak().map {
            if (it is DeltakerHistorikk.EnkeltplassOkonomiGodkjent) {
                it.copy(data = it.data.copy(vedtak = it.data.vedtak?.copy(fattet = null)))
            } else {
                it
            }
        }

        tilJson(historikk)[1]["vedtaksdetaljer"].isNull shouldBe true
    }

    @Test
    fun `manglende prisinformasjon erstattes ikke med senere godkjent pris`() {
        val historikk = lagEnkeltplassHistorikkMedVedtak().map {
            if (it is DeltakerHistorikk.EnkeltplassOkonomiGodkjent && it.data.erForsteGodkjenning) {
                it.copy(data = it.data.copy(prisinformasjon = null))
            } else {
                it
            }
        }

        val response = tilJson(historikk)

        response[1]["prisinformasjon"].isNull shouldBe true
        response[1]["vedtaksdetaljer"]["bakgrunnsinformasjon"].asText() shouldBe "Bakgrunn ved vedtak"
    }

    @Test
    fun `senere endringer erstatter ikke innholdet i vedtaksøyeblikksbildet`() {
        val historikk = lagEnkeltplassHistorikkMedVedtak() +
            DeltakerHistorikk.Endring(lagDeltakerEndring())

        val response = tilJson(historikk)

        response[1]["vedtaksdetaljer"]["bakgrunnsinformasjon"].asText() shouldBe "Bakgrunn ved vedtak"
        response[2]["endring"]["bakgrunnsinformasjon"].asText() shouldBe "Oppdatert bakgrunnsinformasjon"
    }

    @Test
    fun `valgfrie tomme felt i vedtaket forblir tomme`() {
        val historikk = lagEnkeltplassHistorikkMedVedtak().map {
            if (it is DeltakerHistorikk.EnkeltplassOkonomiGodkjent) {
                val vedtak = it.data.vedtak
                it.copy(
                    data = it.data.copy(
                        vedtak = vedtak?.copy(
                            deltakerVedVedtak = vedtak.deltakerVedVedtak.copy(
                                startdato = null,
                                sluttdato = null,
                                deltakelsesinnhold = null,
                                opplaringKategorisering = null,
                                dagerPerUke = null,
                                deltakelsesprosent = null,
                                bakgrunnsinformasjon = null,
                            ),
                        ),
                    ),
                )
            } else {
                it
            }
        }

        val detaljer = tilJson(historikk)[1]["vedtaksdetaljer"]

        detaljer["fattet"].asText() shouldBe "2026-10-15T12:00:00"
        listOf(
            "startdato",
            "sluttdato",
            "deltakelsesinnhold",
            "opplaringKategorisering",
            "dagerPerUke",
            "deltakelsesprosent",
            "bakgrunnsinformasjon",
        ).all { detaljer[it].isNull } shouldBe true
    }

    @Test
    fun `godkjenning med lagret vedtak kan serialiseres og leses tilbake`() {
        val godkjenning = lagEnkeltplassHistorikkMedVedtak()
            .filterIsInstance<DeltakerHistorikk.EnkeltplassOkonomiGodkjent>()
            .single { it.data.erForsteGodkjenning }
            .data
        val json = objectMapper.writeValueAsString(godkjenning)

        objectMapper.readValue<OkonomiGodkjentForHistorikk>(json) shouldBe godkjenning
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
    fun `direkte vedtak beholdes som eget historikkinnslag`() {
        val historikk = lagEnkeltplassHistorikkMedVedtak()

        val response = tilJson(historikk, GjennomforingPameldingType.DIREKTE_VEDTAK)

        response.size() shouldBe 3
        response[2]["type"].asText() shouldBe "Vedtak"
        response[1]["vedtaksdetaljer"] shouldBe response[2]
    }

    private fun tilJson(
        historikk: List<DeltakerHistorikk>,
        pameldingstype: GjennomforingPameldingType? = null,
    ) = objectMapper.readTree(
        objectMapper.writePolymorphicCollectionAsString(
            DeltakerHistorikkResponse.fromModels(
                models = historikk,
                arrangornavn = "Historisk opplæringssted",
                oppstartstype = Oppstartstype.LOPENDE,
                pameldingstype = pameldingstype,
                enheter = lagNavEnheterForHistorikk(historikk).associateBy { it.id },
                ansatte = lagNavAnsatteForHistorikk(historikk).associateBy { it.id },
            ),
        ),
    )
}
