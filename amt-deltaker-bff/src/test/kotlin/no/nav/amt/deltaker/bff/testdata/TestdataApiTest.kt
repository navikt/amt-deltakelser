package no.nav.amt.deltaker.bff.testdata

import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import no.nav.amt.deltaker.bff.utils.IntegrationTestBase
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltaker
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerliste
import no.nav.amt.deltaker.bff.utils.TestData.lagTiltakstype
import no.nav.amt.deltaker.bff.veileder.api.utils.systemPostRequest
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.utils.TestData.randomIdent
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TestdataApiTest : IntegrationTestBase() {
    @Test
    fun `opprett testdata - mangler token - returnerer 401`() {
        withTestApplicationContext { client ->
            client.post("/testdata/opprett") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `opprett testdata - har tilgang, ugyldig request - returnerer BadRequest`() {
        val deltakerliste = lagDeltakerliste(
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
        )
        val startdato = LocalDate.now().minusDays(1)
        val opprettTestDeltakelseRequest = OpprettTestDeltakelseRequest(
            personident = randomIdent(),
            deltakerlisteId = deltakerliste.id,
            startdato = startdato,
            deltakelsesprosent = 100,
            dagerPerUke = 7,
        )

        withTestApplicationContext { client ->
            client.post("/testdata/opprett") { systemPostRequest(opprettTestDeltakelseRequest) }.status shouldBe HttpStatusCode.BadRequest
        }
    }

    @Test
    fun `opprett testdata - har tilgang, gyldig request - returnerer deltaker`() {
        val deltakerliste = lagDeltakerliste(
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
        )
        val startdato = LocalDate.now().minusDays(1)
        val deltaker = lagDeltaker(
            deltakerliste = deltakerliste,
            startdato = startdato,
            sluttdato = startdato.plusMonths(3),
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
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

        withTestApplicationContext { client ->
            client.post("/testdata/opprett") { systemPostRequest(opprettTestDeltakelseRequest) }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(deltaker)
            }
        }
    }
}
