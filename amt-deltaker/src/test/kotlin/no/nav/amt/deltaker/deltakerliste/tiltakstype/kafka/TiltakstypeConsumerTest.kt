package no.nav.amt.deltaker.deltakerliste.tiltakstype.kafka

import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.kafka.AmtGjennomforingProducer
import no.nav.amt.deltaker.repository.DeltakerlisteRepository
import no.nav.amt.deltaker.tiltak.TiltakConsumer
import no.nav.amt.deltaker.tiltak.TiltakRepository
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorRepository
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.lib.models.deltaker.toV2
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.models.kafka.TiltakstypePayload
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagArrangor
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

class TiltakstypeConsumerTest {
    private val tiltakRepository = TiltakRepository()
    private val deltakerlisteRepository = DeltakerlisteRepository()
    private val arrangorRepository = ArrangorRepository()
    private val amtGjennomforingProducer = mockk<AmtGjennomforingProducer>()

    private val consumer = TiltakConsumer(tiltakRepository, deltakerlisteRepository, amtGjennomforingProducer)

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @BeforeEach
    fun setup() {
        clearMocks(amtGjennomforingProducer)
        every { amtGjennomforingProducer.produce(any()) } just runs
    }

    @Test
    fun `consumeTiltakstype - ny, aktiv tiltakstype - lagrer tiltakstype`() {
        val tiltakstype = TestData.lagTiltakstype()

        consumer.consume(tiltakstype.id, objectMapper.writeValueAsString(lagTiltakstypePayload(tiltakstype)))

        tiltakRepository.get(tiltakstype.tiltakskode).shouldBeSuccess() shouldBe tiltakstype
    }

    @Test
    fun `consumeTiltakstype - ny tiltakstype uten eksisterende - reproduserer ingen gjennomforinger`() = runTest {
        val tiltakstype = TestData.lagTiltakstype()

        consumer.consume(tiltakstype.id, objectMapper.writeValueAsString(lagTiltakstypePayload(tiltakstype)))

        verify(exactly = 0) { amtGjennomforingProducer.produce(any()) }
    }

    @Test
    fun `consumeTiltakstype - navn endres - reproduserer tilknyttede gjennomforinger med nytt navn`() = runTest {
        val tiltakstype = TestData.lagTiltakstype(navn = "Opprinnelig navn")
        val gjennomforingId = lagreGjennomforing(tiltakstype)

        val endretTiltakstype = tiltakstype.copy(navn = "Nytt tiltaksnavn")
        consumer.consume(endretTiltakstype.id, objectMapper.writeValueAsString(lagTiltakstypePayload(endretTiltakstype)))

        verify(exactly = 1) {
            amtGjennomforingProducer.produce(
                match { it.id == gjennomforingId && it.tiltak.navn == "Nytt tiltaksnavn" },
            )
        }
    }

    @Test
    fun `consumeTiltakstype - tiltakstype uendret - reproduserer ingen gjennomforinger`() = runTest {
        val tiltakstype = TestData.lagTiltakstype()
        lagreGjennomforing(tiltakstype)

        consumer.consume(tiltakstype.id, objectMapper.writeValueAsString(lagTiltakstypePayload(tiltakstype)))

        verify(exactly = 0) { amtGjennomforingProducer.produce(any()) }
    }

    private fun lagreGjennomforing(tiltakstype: Tiltakstype): UUID {
        val arrangor = lagArrangor()
        arrangorRepository.upsert(arrangor)
        tiltakRepository.upsert(tiltakstype)

        val deltakerliste = lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)
        deltakerlisteRepository.upsert(deltakerliste)

        return deltakerliste.id
    }

    private fun lagTiltakstypePayload(tiltakstype: Tiltakstype) = TiltakstypePayload(
        id = tiltakstype.id,
        navn = tiltakstype.navn,
        tiltakskode = tiltakstype.tiltakskode,
        innsatsgrupper = tiltakstype.innsatsgrupper.map { it.toV2() }.toSet(),
        deltakerRegistreringInnhold = tiltakstype.innhold,
    )
}
