package no.nav.amt.deltaker.deltaker.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.every
import no.nav.amt.deltaker.deltaker.api.utils.postVeilederRequest
import no.nav.amt.deltaker.external.DeltakelserResponseMapper
import no.nav.amt.deltaker.external.data.DeltakelserResponse
import no.nav.amt.deltaker.external.data.DeltakerKort
import no.nav.amt.deltaker.external.data.HentDeltakelserRequest
import no.nav.amt.deltaker.external.data.Periode
import no.nav.amt.deltaker.utils.RouteTestBase
import no.nav.amt.deltaker.utils.data.TestData.lagArrangor
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.deltaker.utils.data.TestData.randomIdent
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.objectMapper
import no.nav.poao_tilgang.client.Decision
import no.nav.poao_tilgang.client.api.ApiResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class HentDeltakelserApiTest : RouteTestBase() {
    override val deltakelserResponseMapper = DeltakelserResponseMapper(deltakerHistorikkService, arrangorService)

    @BeforeEach
    fun setup() = unleashClient.enableAll()

    @Test
    fun `skal teste autentisering - mangler token - returnerer 401`() {
        withTestApplicationContext { client ->
            client
                .post("/deltakelser") {
                    setBody("foo")
                }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `skal teste tilgangskontroll - har ikke tilgang - returnerer 403`() {
        every {
            poaoTilgangCachedClient.evaluatePolicy(any())
        } returns ApiResult(null, Decision.Deny("Ikke tilgang", ""))

        withTestApplicationContext { client ->
            client
                .post("/deltakelser") {
                    postVeilederRequest(deltakelserRequest)
                }.status shouldBe HttpStatusCode.Forbidden
        }
    }

    @Test
    fun `post deltakelser - har tilgang, deltaker deltar - returnerer 200`() {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)

        val innsoktDato = LocalDate.now().minusDays(4)
        val deltaker = lagDeltaker(
            deltakerliste = lagDeltakerliste(
                arrangor = lagArrangor(overordnetArrangorId = null, navn = "ARRANGØR AS"),
                tiltakstype = lagTiltakstype(
                    tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
                    navn = "Arbeidsforberedende trening",
                ),
            ),
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        )
        val historikk = listOf(
            DeltakerHistorikk.Vedtak(
                lagVedtak(opprettet = innsoktDato.atStartOfDay()),
            ),
        )

        every { arrangorService.getArrangorNavn(any()) } returns deltaker.deltakerliste.arrangor.navn

        every { deltakerRepository.getFlereForPerson(any()) } returns listOf(deltaker)
        every { deltakerHistorikkService.getForDeltaker(any()) } returns historikk

        val forventetRespons = DeltakelserResponse(
            aktive = listOf(
                DeltakerKort(
                    deltakerId = deltaker.id,
                    deltakerlisteId = deltaker.deltakerliste.id,
                    tittel = "Arbeidsforberedende trening hos ${deltaker.deltakerliste.arrangor.navn}",
                    tiltakstype = DeltakelserResponse.Tiltakstype(
                        navn = deltaker.deltakerliste.tiltakstype.navn,
                        tiltakskode = deltaker.deltakerliste.tiltakstype.tiltakskode,
                    ),
                    status = DeltakerKort.Status(
                        type = DeltakerStatus.Type.DELTAR,
                        visningstekst = "Deltar",
                        aarsak = null,
                    ),
                    innsoktDato = innsoktDato,
                    sistEndretDato = null,
                    periode = Periode(
                        startdato = deltaker.startdato,
                        sluttdato = deltaker.sluttdato,
                    ),
                ),
            ),
            historikk = emptyList(),
        )

        withTestApplicationContext { client ->
            client.post("/deltakelser") { postVeilederRequest(deltakelserRequest) }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(forventetRespons)
            }
        }
    }

    @Test
    fun `post deltakelser - har tilgang, kladd og avsluttet deltakelse - returnerer 200`() {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        val innsoktDato = LocalDate.now().minusDays(4)
        val deltakerKladd = lagDeltaker(
            deltakerliste = lagDeltakerliste(
                arrangor = lagArrangor(overordnetArrangorId = null, navn = "ARRANGØR AS"),
                tiltakstype = lagTiltakstype(
                    tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
                    navn = "Arbeidsforberedende trening",
                ),
            ),
            status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
        )
        every { arrangorService.getArrangorNavn(any()) } returns deltakerKladd.deltakerliste.arrangor.navn

        val avsluttetDeltaker = lagDeltaker(
            deltakerliste = lagDeltakerliste(
                arrangor = lagArrangor(overordnetArrangorId = null, navn = "ARRANGØR OG SØNN AS"),
                tiltakstype = lagTiltakstype(
                    tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
                    navn = "Arbeidsforberedende trening",
                ),
            ),
            startdato = LocalDate.now().minusMonths(3),
            sluttdato = LocalDate.now().minusDays(2),
            status = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                aarsakType = DeltakerStatus.Aarsak.Type.FATT_JOBB,
            ),
        )
        val deltakerhistorikk = listOf(
            DeltakerHistorikk.Vedtak(
                lagVedtak(
                    opprettet = innsoktDato.atStartOfDay(),
                ),
            ),
        )

        every { deltakerRepository.getFlereForPerson(any()) } returns listOf(deltakerKladd, avsluttetDeltaker)
        every { deltakerHistorikkService.getForDeltaker(deltakerKladd.id) } returns emptyList()
        every { deltakerHistorikkService.getForDeltaker(avsluttetDeltaker.id) } returns deltakerhistorikk

        val forventetRespons = DeltakelserResponse(
            aktive = listOf(
                DeltakerKort(
                    deltakerId = deltakerKladd.id,
                    deltakerlisteId = deltakerKladd.deltakerliste.id,
                    tittel = "Arbeidsforberedende trening hos ${deltakerKladd.deltakerliste.arrangor.navn}",
                    tiltakstype = DeltakelserResponse.Tiltakstype(
                        navn = deltakerKladd.deltakerliste.tiltakstype.navn,
                        tiltakskode = deltakerKladd.deltakerliste.tiltakstype.tiltakskode,
                    ),
                    status = DeltakerKort.Status(
                        type = DeltakerStatus.Type.KLADD,
                        visningstekst = "Kladden er ikke delt",
                        aarsak = null,
                    ),
                    innsoktDato = null,
                    sistEndretDato = deltakerKladd.sistEndret.toLocalDate(),
                    periode = null,
                ),
            ),
            historikk = listOf(
                DeltakerKort(
                    deltakerId = avsluttetDeltaker.id,
                    deltakerlisteId = avsluttetDeltaker.deltakerliste.id,
                    tittel = "Arbeidsforberedende trening hos ${deltakerKladd.deltakerliste.arrangor.navn}",
                    tiltakstype = DeltakelserResponse.Tiltakstype(
                        navn = avsluttetDeltaker.deltakerliste.tiltakstype.navn,
                        tiltakskode = avsluttetDeltaker.deltakerliste.tiltakstype.tiltakskode,
                    ),
                    status = DeltakerKort.Status(
                        type = DeltakerStatus.Type.HAR_SLUTTET,
                        visningstekst = "Har sluttet",
                        aarsak = "Fått jobb",
                    ),
                    innsoktDato = innsoktDato,
                    sistEndretDato = null,
                    periode = Periode(
                        startdato = avsluttetDeltaker.startdato,
                        sluttdato = avsluttetDeltaker.sluttdato,
                    ),
                ),
            ),
        )

        withTestApplicationContext { client ->
            client.post("/deltakelser") { postVeilederRequest(deltakelserRequest) }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(forventetRespons)
            }
        }
    }

    @Test
    fun `post deltakelser - har tilgang, ingen deltakelser - returnerer 200`() {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        every { deltakerRepository.getFlereForPerson(any()) } returns emptyList()

        val forventetRespons = DeltakelserResponse(
            aktive = emptyList(),
            historikk = emptyList(),
        )

        withTestApplicationContext { client ->
            client.post("/deltakelser") { postVeilederRequest(deltakelserRequest) }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(forventetRespons)
            }
        }
    }

    companion object {
        private val deltakelserRequest = HentDeltakelserRequest(randomIdent())
    }
}
