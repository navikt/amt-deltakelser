package no.nav.amt.distribusjon.veilarboppfolging

import com.github.benmanes.caffeine.cache.Cache
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.amt.distribusjon.testEnvironment
import no.nav.amt.distribusjon.utils.ClientTestBase
import no.nav.amt.distribusjon.utils.CountingCache
import no.nav.amt.distribusjon.utils.createMockHttpClient
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class VeilarboppfolgingClientTest : ClientTestBase() {
    @Nested
    inner class OpprettEllerHentSakTests {
        @Test
        fun `skal returnere sak nar opprettEllerHentSak kalles med gyldig respons`() {
            val expectedSak = Sak(oppfolgingsperiodeId, sakId = 42, fagsaksystem = "~fagsaksystem~")

            val sut = createVeilarboppfolgingClient(
                expectedUrl = opprettEllerHentSakUrl,
                responseBody = expectedSak,
            )

            val actualSak = runBlocking {
                sut.opprettEllerHentSak(oppfolgingsperiodeId)
            }

            actualSak shouldBe expectedSak
        }

        @Test
        fun `skal kaste feil nar opprettEllerHentSak returnerer feilkode`() {
            val sut = createVeilarboppfolgingClient(
                expectedUrl = opprettEllerHentSakUrl,
                statusCode = HttpStatusCode.BadRequest,
                responseBody = null,
            )

            val thrown = runBlocking {
                shouldThrow<IllegalStateException> {
                    sut.opprettEllerHentSak(oppfolgingsperiodeId)
                }
            }

            thrown.message shouldStartWith "Kunne ikke hente sak fra veilarboppfolging for oppfolgingsperiode $oppfolgingsperiodeId"
        }
    }

    @Nested
    inner class ErUnderManuellOppfolgingTests {
        @Test
        fun `skal returnere true nar bruker er under manuell oppfolging`() {
            val sut = createVeilarboppfolgingClient(
                expectedUrl = ER_UNDER_MANUELL_OPPFOLGING_URL,
                responseBody = ManuellV2Response(true),
            )

            val result = runBlocking {
                sut.erUnderManuellOppfolging("~personident~")
            }

            result shouldBe true
        }

        @Test
        fun `skal returnere false nar bruker ikke er under manuell oppfolging`() {
            val sut = createVeilarboppfolgingClient(
                expectedUrl = ER_UNDER_MANUELL_OPPFOLGING_URL,
                responseBody = ManuellV2Response(false),
            )

            val result = runBlocking {
                sut.erUnderManuellOppfolging("~personident~")
            }

            result shouldBe false
        }

        @Test
        fun `skal bruke cache ved andre kall til erUnderManuellOppfolging`() {
            val countingCache = CountingCache<String, Boolean>()

            val sut = createVeilarboppfolgingClient(
                expectedUrl = ER_UNDER_MANUELL_OPPFOLGING_URL,
                responseBody = ManuellV2Response(false),
                cache = countingCache,
            )

            runBlocking {
                sut.erUnderManuellOppfolging("~personident~")
                sut.erUnderManuellOppfolging("~personident~")
            }

            countingCache.putCount shouldBe 1
        }

        @Test
        fun `skal kaste feil nar erUnderManuellOppfolging returnerer feilkode`() {
            val sut = createVeilarboppfolgingClient(
                expectedUrl = ER_UNDER_MANUELL_OPPFOLGING_URL,
                responseBody = null,
                statusCode = HttpStatusCode.BadRequest,
            )

            val thrown = runBlocking {
                shouldThrow<IllegalStateException> {
                    sut.erUnderManuellOppfolging("~personident~")
                }
            }

            thrown.message shouldStartWith "Kunne ikke hente manuell oppfølging fra veilarboppfolging"
        }
    }

    private fun <T> createVeilarboppfolgingClient(
        expectedUrl: String,
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        responseBody: T? = null,
        cache: Cache<String, Boolean>? = null,
    ): VeilarboppfolgingClient = if (cache != null) {
        VeilarboppfolgingClient(
            httpClient = createMockHttpClient(expectedUrl, responseBody, statusCode),
            azureAdTokenClient = mockAzureAdTokenClient,
            environment = testEnvironment,
            manuellOppfolgingCache = cache,
        )
    } else {
        VeilarboppfolgingClient(
            httpClient = createMockHttpClient(expectedUrl, responseBody, statusCode),
            azureAdTokenClient = mockAzureAdTokenClient,
            environment = testEnvironment,
        )
    }

    companion object {
        val oppfolgingsperiodeId: UUID = UUID.randomUUID()
        val opprettEllerHentSakUrl = "http://veilarboppfolging/veilarboppfolging/api/v3/sak/$oppfolgingsperiodeId"
        const val ER_UNDER_MANUELL_OPPFOLGING_URL = "http://veilarboppfolging/veilarboppfolging/api/v3/hent-manuell"
    }
}
