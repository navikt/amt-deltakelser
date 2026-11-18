package no.nav.amt.deltaker.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import no.nav.amt.deltaker.api.response.DeltakerResponseBuilder
import no.nav.amt.deltaker.repository.GjennomforingRepository
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.utils.IntegrationTestBase
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

class GjennomforingApiTest : IntegrationTestBase() {
    override val arrangorService: ArrangorService = mockk()
    override val gjennomforingRepository: GjennomforingRepository = mockk()

    @Test
    fun `get gjennomforing - mangler token - returnerer 401`() {
        withTestApplicationContext { client ->
            client.get("/gjennomforing/${UUID.randomUUID()}").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    @DisplayName("GET /gjennomforing/{id} - gjennomføring finnes - returnerer 200 med gjennomføring")
    fun `get gjennomforing - gjennomføring finnes - returnerer 200 med gjennomføring`() {
        val deltakerliste = lagDeltakerliste()

        every { gjennomforingRepository.get(deltakerliste.id) } returns deltakerliste

        val expectedResponse = DeltakerResponseBuilder(
            navAnsattService = mockk(),
            navEnhetService = mockk(),
            digitalBrukerService = mockk(),
            deltakerHistorikkService = mockk(),
            forslagRepository = mockk(),
            deltakerLaaseService = mockk(),
            vurderingRepository = mockk(),
            deltakerRepository = mockk(),
        ).buildGjennomforingResponse(
            gjennomforing = deltakerliste,
            includeOpplaringKategorisering = false,
        )

        withTestApplicationContext { client ->
            client.get("/gjennomforing/${deltakerliste.id}") { noBodyRequest() }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(expectedResponse)
            }
        }
    }

    @Test
    @DisplayName("GET /gjennomforing/{id} - gjennomføring finnes ikke - returnerer 404")
    fun `get gjennomforing - gjennomføring finnes ikke - returnerer 404`() {
        every {
            gjennomforingRepository.get(any())
        } returns null

        withTestApplicationContext { client ->
            client.get("/gjennomforing/${UUID.randomUUID()}") { noBodyRequest() }.apply {
                status shouldBe HttpStatusCode.NotFound
            }
        }
    }
}
