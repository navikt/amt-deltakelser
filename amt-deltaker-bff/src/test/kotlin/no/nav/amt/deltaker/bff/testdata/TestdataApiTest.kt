package no.nav.amt.deltaker.bff.testdata

import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.application.plugins.configureAuthentication
import no.nav.amt.deltaker.bff.application.plugins.configureRouting
import no.nav.amt.deltaker.bff.application.plugins.configureSerialization
import no.nav.amt.deltaker.bff.deltaker.api.utils.systemPostRequest
import no.nav.amt.deltaker.bff.utils.configureEnvForAuthentication
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TestdataApiTest {
    private val testdataService = mockk<TestdataService>()

    @BeforeEach
    fun setup() {
        configureEnvForAuthentication()
    }

    @Test
    fun `opprett testdata - mangler token - returnerer 401`() = testApplication {
        setUpTestApplication()
        client.post("/testdata/opprett") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `opprett testdata - har tilgang, ugyldig request - returnerer BadRequest`() = testApplication {
        val deltakerliste = TestData.lagDeltakerliste(
            tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
        )
        val startdato = LocalDate.now().minusDays(1)
        val opprettTestDeltakelseRequest = OpprettTestDeltakelseRequest(
            personident = TestData.randomIdent(),
            deltakerlisteId = deltakerliste.id,
            startdato = startdato,
            deltakelsesprosent = 100,
            dagerPerUke = 7,
        )

        setUpTestApplication()

        client.post("/testdata/opprett") { systemPostRequest(opprettTestDeltakelseRequest) }.apply {
            status shouldBe HttpStatusCode.BadRequest
        }
    }

    @Test
    fun `opprett testdata - har tilgang, gyldig request - returnerer deltaker`() = testApplication {
        val deltakerliste = TestData.lagDeltakerliste(
            tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
        )
        val startdato = LocalDate.now().minusDays(1)
        val deltaker = TestData.lagDeltaker(
            deltakerliste = deltakerliste,
            startdato = startdato,
            sluttdato = startdato.plusMonths(3),
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            deltakelsesprosent = 50F,
            dagerPerUke = 3F,
        )
        val opprettTestDeltakelseRequest = OpprettTestDeltakelseRequest(
            personident = deltaker.navBruker.personident,
            deltakerlisteId = deltaker.deltakerliste.id,
            startdato = startdato,
            deltakelsesprosent = deltaker.deltakelsesprosent?.toInt()!!,
            dagerPerUke = deltaker.dagerPerUke?.toInt(),
        )

        coEvery { testdataService.opprettDeltakelse(any()) } returns deltaker

        setUpTestApplication()

        client.post("/testdata/opprett") { systemPostRequest(opprettTestDeltakelseRequest) }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writeValueAsString(deltaker)
        }
    }

    private fun ApplicationTestBuilder.setUpTestApplication() {
        application {
            configureSerialization()
            configureAuthentication(Environment())
            configureRouting(
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                testdataService,
            )
        }
    }
}
