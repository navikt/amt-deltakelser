package no.nav.amt.deltaker.api

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.mockk.every
import no.nav.amt.deltaker.deltaker.api.utils.postVeilederRequest
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.external.DeltakelserResponseMapper
import no.nav.amt.deltaker.external.data.DeltakelserResponse
import no.nav.amt.deltaker.external.data.DeltakerKort
import no.nav.amt.deltaker.external.data.DeltakerPersonaliaResponse
import no.nav.amt.deltaker.external.data.HentDeltakelserRequest
import no.nav.amt.deltaker.external.data.Periode
import no.nav.amt.deltaker.utils.RouteTestBase
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.lagArrangor
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.deltaker.utils.data.TestData.randomIdent
import no.nav.amt.deltaker.utils.generateJWT
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.utils.objectMapper
import no.nav.poao_tilgang.client.Decision
import no.nav.poao_tilgang.client.api.ApiResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class ExternalApiTest : RouteTestBase() {
    override val deltakelserResponseMapper = DeltakelserResponseMapper(deltakerHistorikkService, arrangorService)

    @BeforeEach
    fun setup() = unleashClient.enableAll()

    @Nested
    inner class DeltakelserTests {
        private val deltakelserRequest = HentDeltakelserRequest(randomIdent())

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
    }

    @Test
    fun `autentisering tester - ulike scenarioer`() {
        val deltakerIds = listOf(UUID.randomUUID())

        withTestApplicationContext { client ->
            val tomtToken = null
            client.postPersonalia(deltakerIds, tomtToken).status shouldBe HttpStatusCode.Unauthorized

            val ugyldigToken = "ugyldig-token"
            client.postPersonalia(deltakerIds, ugyldigToken).status shouldBe HttpStatusCode.Unauthorized

            val feilApp = "feil-app"
            val feilToken = generateJWT(consumerClientId = feilApp, audience = "amt-deltaker")
            client.postPersonalia(deltakerIds, feilToken).status shouldBe HttpStatusCode.Unauthorized

            val preauthorizedAppUtenTilgang = "amt-deltaker-bff"
            val utenTilgangToken =
                generateJWT(consumerClientId = preauthorizedAppUtenTilgang, audience = "amt-deltaker")
            client.postPersonalia(deltakerIds, utenTilgangToken).status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `post deltaker personalia - standard scenario`() {
        val (deltaker, navEnhet) = createDeltakerWithNavEnhet("12345678901", "Test", "Testesen", "Midt")

        mockServices(listOf(deltaker), navEnhet?.let { mapOf(it.id to it) } ?: emptyMap())

        val forventetRespons = forventetResponse(deltaker, navEnhet)

        withTestApplicationContext { client ->
            client.postPersonalia(listOf(deltaker.id)).apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(forventetRespons)
            }
        }
    }

    @Test
    fun `post deltaker personalia - deltaker med adressebeskyttelse - returnerer korrekt adressebeskyttelse`() {
        val (deltaker, navEnhet) = createDeltakerWithNavEnhet(
            personident = "98765432109",
            fornavn = "Fortrolig",
            etternavn = "Person",
            mellomnavn = null,
            enhetsnummer = "5678",
            enhetsnavn = "Nav Fortrolig",
            erSkjermet = true,
            adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG,
        )

        mockServices(listOf(deltaker), navEnhet?.let { mapOf(it.id to it) } ?: emptyMap())

        val forventetRespons = forventetResponse(deltaker, navEnhet)

        withTestApplicationContext { client ->
            client.postPersonalia(listOf(deltaker.id)).apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(forventetRespons)
            }
        }
    }

    @Test
    fun `post deltaker personalia - deltaker uten navEnhet - returnerer null for navEnhetsnummer`() {
        val (deltaker, _) = createDeltakerWithNavEnhet(
            personident = "11223344556",
            fornavn = "Uten",
            etternavn = "NavEnhet",
            navEnhetId = null,
        )

        mockServices(listOf(deltaker), emptyMap())

        val forventetRespons = forventetResponse(deltaker, null)

        withTestApplicationContext { client ->
            client.postPersonalia(listOf(deltaker.id)).apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(forventetRespons)
            }
        }
    }

    @Test
    fun `post deltaker personalia - flere deltakere - returnerer alle personalia`() {
        val navEnhet1 = TestData.lagNavEnhet(enhetsnummer = "1111", navn = "Nav En")
        val navEnhet2 = TestData.lagNavEnhet(enhetsnummer = "2222", navn = "Nav To")

        val deltaker1 = lagDeltaker(
            navBruker = TestData.lagNavBruker(
                personident = "11111111111",
                fornavn = "Person",
                etternavn = "En",
                navEnhetId = navEnhet1.id,
            ),
        )
        val deltaker2 = lagDeltaker(
            navBruker = TestData.lagNavBruker(
                personident = "22222222222",
                fornavn = "Person",
                etternavn = "To",
                navEnhetId = navEnhet2.id,
            ),
        )

        mockServices(
            listOf(deltaker1, deltaker2),
            mapOf(navEnhet1.id to navEnhet1, navEnhet2.id to navEnhet2),
        )

        withTestApplicationContext { client ->
            client.postPersonalia(listOf(deltaker1.id, deltaker2.id)).apply {
                status shouldBe HttpStatusCode.OK
                val response = objectMapper.readValue(bodyAsText(), Array<DeltakerPersonaliaResponse>::class.java).toList()
                response.size shouldBe 2
                response.find { it.deltakerId == deltaker1.id }?.navEnhetsnummer shouldBe "1111"
                response.find { it.deltakerId == deltaker2.id }?.navEnhetsnummer shouldBe "2222"
            }
        }
    }

    @Test
    fun `post deltaker personalia - tom liste - returnerer tom liste`() {
        val deltakerIds = emptyList<UUID>()

        mockServices(emptyList(), emptyMap())

        withTestApplicationContext { client ->
            client.postPersonalia(deltakerIds).apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe "[ ]"
            }
        }
    }

    private fun mockServices(
        deltakere: List<Deltaker>,
        navEnheter: Map<UUID, NavEnhet> = emptyMap(),
    ) {
        every { deltakerRepository.getMany(any()) } returns deltakere
        every { navEnhetService.getEnheter(any()) } returns navEnheter
    }

    companion object {
        private val mulighetsrommetSystemToken = generateJWT(
            consumerClientId = "mulighetsrommet-api",
            audience = "amt-deltaker",
        )

        private fun forventetResponse(
            deltaker: Deltaker,
            navEnhet: NavEnhet?,
        ): List<DeltakerPersonaliaResponse> {
            val forventetRespons = listOf(
                DeltakerPersonaliaResponse.from(
                    deltaker,
                    navEnhet?.let { mapOf(navEnhet.id to navEnhet) }
                        ?: emptyMap(),
                ),
            )
            return forventetRespons
        }

        private fun createStandardRequest(deltakerIds: List<UUID>) = objectMapper.writeValueAsString(deltakerIds)

        private suspend fun HttpClient.postPersonalia(
            deltakerIds: List<UUID>,
            token: String? = mulighetsrommetSystemToken,
        ): HttpResponse = post("/external/deltakere/personalia") {
            setBody(createStandardRequest(deltakerIds))
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            token?.let { bearerAuth(it) }
        }

        private fun createDeltakerWithNavEnhet(
            personident: String,
            fornavn: String,
            etternavn: String,
            mellomnavn: String? = null,
            enhetsnummer: String = "1234",
            enhetsnavn: String = "Nav Test",
            erSkjermet: Boolean = false,
            adressebeskyttelse: Adressebeskyttelse? = null,
            navEnhetId: UUID? = null,
        ): Pair<Deltaker, NavEnhet?> {
            val navEnhet = if (navEnhetId != null) null else TestData.lagNavEnhet(enhetsnummer = enhetsnummer, navn = enhetsnavn)
            val deltaker = lagDeltaker(
                navBruker = TestData.lagNavBruker(
                    personident = personident,
                    fornavn = fornavn,
                    mellomnavn = mellomnavn,
                    etternavn = etternavn,
                    navEnhetId = navEnhetId ?: navEnhet?.id,
                    erSkjermet = erSkjermet,
                    adressebeskyttelse = adressebeskyttelse,
                ),
            )
            return deltaker to navEnhet
        }
    }
}
