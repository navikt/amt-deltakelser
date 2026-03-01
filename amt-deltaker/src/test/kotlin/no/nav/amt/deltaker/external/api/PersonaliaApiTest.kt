package no.nav.amt.deltaker.external.api

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
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.external.data.DeltakerPersonaliaResponse
import no.nav.amt.deltaker.utils.RouteTestBase
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.generateJWT
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class PersonaliaApiTest : RouteTestBase() {
    @BeforeEach
    fun setup() = unleashClient.enableAll()

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
            val utenTilgangToken = generateJWT(consumerClientId = preauthorizedAppUtenTilgang, audience = "amt-deltaker")
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

        val deltaker1 = TestData.lagDeltaker(
            navBruker = TestData.lagNavBruker(
                personident = "11111111111",
                fornavn = "Person",
                etternavn = "En",
                navEnhetId = navEnhet1.id,
            ),
        )
        val deltaker2 = TestData.lagDeltaker(
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

    private fun mockServices(deltakere: List<Deltaker>, navEnheter: Map<UUID, NavEnhet> = emptyMap()) {
        every { deltakerRepository.getMany(any()) } returns deltakere
        every { navEnhetService.getEnheter(any()) } returns navEnheter
    }

    companion object {
        private val mulighetsrommetSystemToken = generateJWT(
            consumerClientId = "mulighetsrommet-api",
            audience = "amt-deltaker",
        )

        private fun forventetResponse(deltaker: Deltaker, navEnhet: NavEnhet?): List<DeltakerPersonaliaResponse> {
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

        private suspend fun HttpClient.postPersonalia(deltakerIds: List<UUID>, token: String? = mulighetsrommetSystemToken): HttpResponse =
            post("/external/deltakere/personalia") {
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
            val deltaker = TestData.lagDeltaker(
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
