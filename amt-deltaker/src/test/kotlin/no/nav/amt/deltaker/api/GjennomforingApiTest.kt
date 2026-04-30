package no.nav.amt.deltaker.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import no.nav.amt.deltaker.api.response.ResponseBuilder
import no.nav.amt.deltaker.repository.DeltakerlisteRepository
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.utils.IntegrationTestBase
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import java.util.UUID

class GjennomforingApiTest : IntegrationTestBase() {
    override val arrangorService: ArrangorService = mockk()
    override val deltakerlisteRepository: DeltakerlisteRepository = mockk()

    @Test
    fun `get gjennomforing - mangler token - returnerer 401`() {
        withTestApplicationContext { client ->
            client.get("/gjennomforing/${UUID.randomUUID()}").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `get gjennomforing - gjennomforing finnes - returnerer 200 med gjennomforing`() {
        val deltakerliste = lagDeltakerliste()

        every { deltakerlisteRepository.get(deltakerliste.id) } returns Result.success(deltakerliste)
        every { arrangorService.getArrangorNavn(deltakerliste.arrangor!!) } returns "Arrangor Navn"

        val expectedResponse = ResponseBuilder(
            arrangorService = arrangorService,
            navAnsattService = mockk(),
            navEnhetService = mockk(),
            amtDistribusjonClient = mockk(),
            deltakerHistorikkService = mockk(),
            forslagRepository = mockk(),
            deltakerLaaseService = mockk(),
            vurderingRepository = mockk(),
        ).buildGjennomforingResponse(deltakerliste)

        withTestApplicationContext { client ->
            client.get("/gjennomforing/${deltakerliste.id}") { noBodyRequest() }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(expectedResponse)
            }
        }
    }

    @Test
    fun `get gjennomforing - gjennomforing finnes ikke - returnerer 404`() {
        every {
            deltakerlisteRepository.get(any())
        } returns Result.failure(NoSuchElementException("Fant ikke deltakerliste"))

        withTestApplicationContext { client ->
            client.get("/gjennomforing/${UUID.randomUUID()}") { noBodyRequest() }.apply {
                status shouldBe HttpStatusCode.NotFound
            }
        }
    }
}
