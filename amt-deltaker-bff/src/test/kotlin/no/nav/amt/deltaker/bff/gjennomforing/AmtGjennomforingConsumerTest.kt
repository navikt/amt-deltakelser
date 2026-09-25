package no.nav.amt.deltaker.bff.gjennomforing

import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.SelfServiceTilgangService
import no.nav.amt.deltaker.bff.tiltak.TiltakRepository
import no.nav.amt.deltaker.bff.tiltaksarrangor.ArrangorRepository
import no.nav.amt.deltaker.bff.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagArrangor
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

class AmtGjennomforingConsumerTest {
    private val deltakerlisteRepository = DeltakerlisteRepository()
    private val tiltakRepository = TiltakRepository()
    private val arrangorRepository = ArrangorRepository()
    private val arrangorService = mockk<ArrangorService>()
    private val selfServiceTilgangService = mockk<SelfServiceTilgangService>(relaxed = true)

    private val sut = AmtGjennomforingConsumer(
        deltakerlisteRepository = deltakerlisteRepository,
        tiltakRepository = tiltakRepository,
        arrangorService = arrangorService,
        selfServiceTilgangService = selfServiceTilgangService,
    )

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    /**
     * I produksjon persisterer [ArrangorService.hentArrangor] arrangøren. Her er den mocket, så vi
     * upserter arrangøren selv for å tilfredsstille FK-en `deltakerliste.arrangor_id`.
     */
    private fun gittArrangor(arrangor: Arrangor) {
        arrangorRepository.upsert(arrangor)
        coEvery { arrangorService.hentArrangor(arrangor.organisasjonsnummer) } returns arrangor
    }

    @Test
    fun `consume - ny gjennomforing - lagrer tiltak og deltakerliste`() {
        val arrangor = lagArrangor()
        val tiltakstype = TestData.lagTiltakstype()
        val payload = TestData.lagAmtGjennomforingPayload(
            tiltakstype = tiltakstype,
            organisasjonsnummer = arrangor.organisasjonsnummer,
        )
        gittArrangor(arrangor)

        runTest {
            sut.consume(payload.id, objectMapper.writeValueAsString(payload))

            tiltakRepository.get(tiltakstype.tiltakskode).shouldBeSuccess() shouldBe TestData.tiltakAv(tiltakstype)

            val lagret = deltakerlisteRepository.get(payload.id).shouldBeSuccess()
            lagret.id shouldBe payload.id
            lagret.status shouldBe payload.status
            lagret.sluttDato shouldBe payload.sluttDato
            lagret.oppstart shouldBe payload.oppstart
            lagret.pameldingstype shouldBe payload.pameldingstype
            lagret.arrangor.organisasjonsnummer shouldBe arrangor.organisasjonsnummer
        }
    }

    @Test
    fun `consume - eksisterende gjennomforing oppdateres - upserter deltakerliste`() {
        val arrangor = lagArrangor()
        val tiltakstype = TestData.lagTiltakstype()
        val id = UUID.randomUUID()
        val payload = TestData.lagAmtGjennomforingPayload(
            id = id,
            tiltakstype = tiltakstype,
            organisasjonsnummer = arrangor.organisasjonsnummer,
            status = GjennomforingStatusType.GJENNOMFORES,
        )
        gittArrangor(arrangor)

        runTest {
            sut.consume(id, objectMapper.writeValueAsString(payload))

            val oppdatert = payload.copy(status = GjennomforingStatusType.AVSLUTTET)
            sut.consume(id, objectMapper.writeValueAsString(oppdatert))

            deltakerlisteRepository.get(id).shouldBeSuccess().status shouldBe GjennomforingStatusType.AVSLUTTET
        }
    }

    @Test
    fun `consume - status AVLYST - stenger tilganger til deltakerliste`() {
        val arrangor = lagArrangor()
        val payload = TestData.lagAmtGjennomforingPayload(
            organisasjonsnummer = arrangor.organisasjonsnummer,
            status = GjennomforingStatusType.AVLYST,
        )
        gittArrangor(arrangor)

        runTest {
            sut.consume(payload.id, objectMapper.writeValueAsString(payload))

            verify(exactly = 1) { selfServiceTilgangService.stengTilgangerTilDeltakerliste(payload.id) }
        }
    }

    @Test
    fun `consume - status AVBRUTT - stenger tilganger til deltakerliste`() {
        val arrangor = lagArrangor()
        val payload = TestData.lagAmtGjennomforingPayload(
            organisasjonsnummer = arrangor.organisasjonsnummer,
            status = GjennomforingStatusType.AVBRUTT,
        )
        gittArrangor(arrangor)

        runTest {
            sut.consume(payload.id, objectMapper.writeValueAsString(payload))

            verify(exactly = 1) { selfServiceTilgangService.stengTilgangerTilDeltakerliste(payload.id) }
        }
    }

    @Test
    fun `consume - aktiv gjennomforing - stenger ikke tilganger`() {
        val arrangor = lagArrangor()
        val payload = TestData.lagAmtGjennomforingPayload(
            organisasjonsnummer = arrangor.organisasjonsnummer,
            status = GjennomforingStatusType.GJENNOMFORES,
        )
        gittArrangor(arrangor)

        runTest {
            sut.consume(payload.id, objectMapper.writeValueAsString(payload))

            verify(exactly = 0) { selfServiceTilgangService.stengTilgangerTilDeltakerliste(any()) }
        }
    }

    @Test
    fun `consume - tombstone - sletter deltakerliste`() {
        val arrangor = lagArrangor()
        val tiltakstype = TestData.lagTiltakstype()
        val payload = TestData.lagAmtGjennomforingPayload(
            tiltakstype = tiltakstype,
            organisasjonsnummer = arrangor.organisasjonsnummer,
        )
        gittArrangor(arrangor)

        runTest {
            sut.consume(payload.id, objectMapper.writeValueAsString(payload))
            deltakerlisteRepository.get(payload.id).shouldBeSuccess()

            sut.consume(payload.id, null)

            deltakerlisteRepository.get(payload.id).shouldBeFailure<NoSuchElementException>()
        }
    }
}
