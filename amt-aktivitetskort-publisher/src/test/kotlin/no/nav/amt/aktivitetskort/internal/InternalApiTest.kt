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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.UUID

@AutoConfigureMockMvc
class InternalApiTest(
    private val mockMvc: MockMvc,
    @MockkBean private val aktivitetskortProducer: AktivitetskortProducer,
    private val meldingRepository: MeldingRepository,
) : IntegrationTestBase() {
    @ParameterizedTest
    @ValueSource(strings = ["KOMET", "ARENA"])
    fun `opprett nye kort lager en ny ID og avviser neste forsøk når perioden er lagret`(kilde: String) {
        val ctx = TestData.MockContext(deltaker = TestData.lagDeltaker(kilde = Kilde.valueOf(kilde)))
        testDatabase.insertArrangor(ctx.arrangor)
        testDatabase.insertDeltakerliste(ctx.deltakerliste)
        testDatabase.insertDeltaker(ctx.deltaker)
        meldingRepository.upsert(ctx.melding.copy(oppfolgingperiode = null))
        val periode = TestData.oppfolgingsperiode()
        every { amtDeltakerClient.getDeltaker(ctx.deltaker.id) } answers {
            TransactionSynchronizationManager.isActualTransactionActive() shouldBe false
            TestData.lagDeltakerResponse(ctx.deltaker, ctx.deltakerliste, ctx.arrangor)
        }
        every { veilarboppfolgingClient.hentOppfolgingperiode(any()) } returns periode
        every { amtArenaAclClient.getArenaIdForAmtId(ctx.deltaker.id) } returns 1L
        every { aktivitetArenaAclClient.getAktivitetIdForArenaId(1L) } returns ctx.melding.id
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

        mockMvc
            .get("/internal/opprett-nye-kort") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"deltakere":["${ctx.deltaker.id}"]}"""
            }.andExpect { status { isForbidden() } }
        meldingRepository.getByDeltakerId(ctx.deltaker.id).size shouldBe 2
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `publisering og nytt kort returnerer 409 hvis historikken endres under HTTP-kallet`(nyttKort: Boolean) {
        val ctx = TestData.MockContext(deltaker = TestData.lagDeltaker(kilde = Kilde.KOMET))
        testDatabase.insertArrangor(ctx.arrangor)
        testDatabase.insertDeltakerliste(ctx.deltakerliste)
        testDatabase.insertDeltaker(ctx.deltaker)
        meldingRepository.upsert(ctx.melding.copy(oppfolgingperiode = null))
        val vinner = ctx.melding.copy(oppfolgingperiode = null, aktivitetskort = ctx.aktivitetskort.copy(tittel = "Nyere kort"))
        every { amtDeltakerClient.getDeltaker(ctx.deltaker.id) } answers {
            TransactionSynchronizationManager.isActualTransactionActive() shouldBe false
            meldingRepository.upsert(vinner)
            TestData.lagDeltakerResponse(ctx.deltaker, ctx.deltakerliste, ctx.arrangor)
        }
        every { veilarboppfolgingClient.hentOppfolgingperiode(any()) } answers {
            TransactionSynchronizationManager.isActualTransactionActive() shouldBe false
            TestData.oppfolgingsperiode()
        }

        if (nyttKort) {
            mockMvc
                .get("/internal/opprett-nye-kort") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"deltakere":["${ctx.deltaker.id}"]}"""
                }.andExpect { status { isConflict() } }
        } else {
            mockMvc.get("/internal/publiser/${ctx.deltaker.id}").andExpect { status { isConflict() } }
        }

        meldingRepository.getByDeltakerId(ctx.deltaker.id).single().aktivitetskort shouldBe vinner.aktivitetskort
        verify(exactly = 0) { aktivitetskortProducer.send(any()) }
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
}
