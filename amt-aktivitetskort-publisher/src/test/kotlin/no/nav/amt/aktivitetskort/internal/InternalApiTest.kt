package no.nav.amt.aktivitetskort.internal

import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.verify
import no.nav.amt.aktivitetskort.IntegrationTestBase
import no.nav.amt.aktivitetskort.database.TestData
import no.nav.amt.aktivitetskort.kafka.producer.AktivitetskortProducer
import no.nav.amt.aktivitetskort.repositories.MeldingRepository
import no.nav.amt.lib.models.deltaker.Kilde
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

@AutoConfigureMockMvc
class InternalApiTest(
    private val mockMvc: MockMvc,
    @MockkBean private val aktivitetskortProducer: AktivitetskortProducer,
    private val meldingRepository: MeldingRepository,
) : IntegrationTestBase() {
    @Test
    fun `publiser regenererer kort fra amt-deltaker med gjeldende periode`() {
        val ctx = TestData.MockContext(deltaker = TestData.lagDeltaker(kilde = Kilde.KOMET))
        val periode = TestData.oppfolgingsperiode()
        lagreMelding(ctx)
        every { amtDeltakerClient.getDeltaker(ctx.deltaker.id) } returns TestData.lagDeltakerResponse(
            ctx.deltaker,
            ctx.deltakerliste,
            ctx.arrangor,
        )
        every { veilarboppfolgingClient.hentOppfolgingperiode(ctx.deltaker.personident) } returns periode
        every { aktivitetskortProducer.send(any()) } just Runs

        mockMvc.get("/internal/publiser/${ctx.deltaker.id}").andExpect { status { isOk() } }

        val melding = meldingRepository.getByDeltakerId(ctx.deltaker.id).single()
        melding.id shouldBe ctx.melding.id
        melding.oppfolgingperiode shouldBe periode.id
        verify(exactly = 1) { aktivitetskortProducer.send(melding.aktivitetskort) }
    }

    @Test
    fun `opprett-nye-kort lager ny Komet-identitet for eldre melding uten periode`() {
        val ctx = TestData.MockContext(deltaker = TestData.lagDeltaker(kilde = Kilde.KOMET))
        val periode = TestData.oppfolgingsperiode()
        testDatabase.insertDeltaker(ctx.deltaker)
        lagreMelding(ctx)
        every { amtDeltakerClient.getDeltaker(ctx.deltaker.id) } returns TestData.lagDeltakerResponse(
            ctx.deltaker,
            ctx.deltakerliste,
            ctx.arrangor,
        )
        every { veilarboppfolgingClient.hentOppfolgingperiode(ctx.deltaker.personident) } returns periode
        every { aktivitetskortProducer.send(any()) } just Runs

        mockMvc
            .get("/internal/opprett-nye-kort") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"deltakere":["${ctx.deltaker.id}"]}"""
            }.andExpect { status { isOk() } }

        val meldinger = meldingRepository.getByDeltakerId(ctx.deltaker.id)
        meldinger.size shouldBe 2
        val nyMelding = meldinger.single { it.oppfolgingperiode == periode.id }
        nyMelding.id shouldNotBe ctx.melding.id
        verify(exactly = 1) { aktivitetskortProducer.send(nyMelding.aktivitetskort) }
    }

    @Test
    fun `slettAktivitetskort - kall fra intern ip - kasserer aktivitetskort med riktige parametre`() {
        val aktivitetskortId = UUID.randomUUID()
        val body = """{"aktivitetskortId": "$aktivitetskortId", "personIdent": "12345678901", "navIdent": "Z123456"}"""

        every {
            aktivitetskortProducer.slettAktivitetskort(any(), any(), any())
        } just Runs

        mockMvc
            .post("/internal/slett/") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isOk() } }

        verify { aktivitetskortProducer.slettAktivitetskort(aktivitetskortId, "12345678901", "Z123456") }
    }

    @Test
    fun `slettAktivitetskort - manglende felt i body - returnerer 400`() {
        mockMvc
            .post("/internal/slett/") {
                contentType = MediaType.APPLICATION_JSON
                content = """{ "aktivitetskortId": "${UUID.randomUUID()}" }"""
            }.andExpect { status { isBadRequest() } }
    }

    private fun lagreMelding(ctx: TestData.MockContext) {
        testDatabase.insertDeltakerliste(ctx.deltakerliste)
        meldingRepository.upsert(ctx.melding)
    }
}
