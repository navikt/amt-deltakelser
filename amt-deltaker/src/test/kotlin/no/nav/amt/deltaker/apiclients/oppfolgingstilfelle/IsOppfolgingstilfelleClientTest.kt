package no.nav.amt.deltaker.apiclients.oppfolgingstilfelle

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.utils.ISOPPFOLGINGSTILFELLE_URL
import no.nav.amt.deltaker.utils.MockResponseHandler
import no.nav.amt.deltaker.utils.createMockHttpClient
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.mockAzureAdClient
import no.nav.amt.deltaker.utils.mockIsOppfolgingstilfelleClient
import org.junit.jupiter.api.Test
import java.time.LocalDate

class IsOppfolgingstilfelleClientTest {
    @Test
    fun `erSykmeldtMedArbeidsgiver returnerer feilkode`() = runTest {
        val oppfolgingstilfelleClient = IsOppfolgingstilfelleClient(
            baseUrl = ISOPPFOLGINGSTILFELLE_URL,
            scope = "scope",
            httpClient = createMockHttpClient(
                expectedUrl = "${ISOPPFOLGINGSTILFELLE_URL}/api/system/v1/oppfolgingstilfelle/personident",
                responseBody = null,
                statusCode = HttpStatusCode.BadRequest,
            ),
            azureAdTokenClient = mockAzureAdClient(),
        )

        val thrown = shouldThrow<RuntimeException> {
            oppfolgingstilfelleClient.erSykmeldtMedArbeidsgiver(personIdentInTest)
        }

        thrown.message shouldStartWith "Kunne ikke hente oppfølgingstilfelle fra isoppfolgingstilfelle"
    }

    @Test
    fun `erSykmeldtMedArbeidsgiver - ingen oppfolgingstilfeller - returnerer false`() = runTest {
        MockResponseHandler.addOppfolgingstilfelleRespons(OppfolgingstilfellePersonResponse(emptyList()))

        isOppfolgingstilfelleClient.erSykmeldtMedArbeidsgiver(personIdentInTest) shouldBe false
    }

    @Test
    fun `erSykmeldtMedArbeidsgiver - har oppfolgingstilfelle, ikke arbeidsgiver - returnerer false`() = runTest {
        MockResponseHandler.addOppfolgingstilfelleRespons(
            OppfolgingstilfellePersonResponse(
                listOf(
                    OppfolgingstilfelleDTO(
                        arbeidstakerAtTilfelleEnd = false,
                        start = LocalDate.now().minusMonths(3),
                        end = LocalDate.now().plusDays(1),
                    ),
                ),
            ),
        )

        isOppfolgingstilfelleClient.erSykmeldtMedArbeidsgiver(personIdentInTest) shouldBe false
    }

    @Test
    fun `erSykmeldtMedArbeidsgiver - oppfolgingstilfelle avsluttet - returnerer false`() = runTest {
        MockResponseHandler.addOppfolgingstilfelleRespons(
            OppfolgingstilfellePersonResponse(
                listOf(
                    OppfolgingstilfelleDTO(
                        arbeidstakerAtTilfelleEnd = true,
                        start = LocalDate.now().minusMonths(3),
                        end = LocalDate.now().minusDays(1),
                    ),
                ),
            ),
        )

        isOppfolgingstilfelleClient.erSykmeldtMedArbeidsgiver(personIdentInTest) shouldBe false
    }

    @Test
    fun `erSykmeldtMedArbeidsgiver - har oppfolgingstilfelle og arbeidsgiver - returnerer true`() = runTest {
        MockResponseHandler.addOppfolgingstilfelleRespons(
            OppfolgingstilfellePersonResponse(
                listOf(
                    OppfolgingstilfelleDTO(
                        arbeidstakerAtTilfelleEnd = true,
                        start = LocalDate.now().minusMonths(3),
                        end = LocalDate.now().plusWeeks(1),
                    ),
                ),
            ),
        )

        isOppfolgingstilfelleClient.erSykmeldtMedArbeidsgiver(personIdentInTest) shouldBe true
    }

    companion object {
        private val isOppfolgingstilfelleClient = mockIsOppfolgingstilfelleClient()
        private val personIdentInTest = TestData.randomIdent()
    }
}
