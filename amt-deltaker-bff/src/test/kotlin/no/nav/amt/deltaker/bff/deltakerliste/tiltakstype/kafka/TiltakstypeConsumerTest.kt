package no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.kafka

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.lib.models.deltaker.toV2
import no.nav.amt.lib.models.deltakerliste.tiltakstype.kafka.TiltakstypeDto
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class TiltakstypeConsumerTest {
    private val tiltakstypeRepository = TiltakstypeRepository()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `consumeTiltakstype - ny, aktiv tiltakstype - lagrer tiltakstype`() {
        val tiltakstype = TestData.lagTiltakstype()
        val tiltakstypeDto = TiltakstypeDto(
            id = tiltakstype.id,
            navn = tiltakstype.navn,
            tiltakskode = tiltakstype.tiltakskode,
            innsatsgrupper = tiltakstype.innsatsgrupper.map { it.toV2() }.toSet(),
            deltakerRegistreringInnhold = tiltakstype.innhold,
        )
        val consumer = TiltakstypeConsumer(tiltakstypeRepository)

        runBlocking {
            consumer.consume(
                tiltakstype.id,
                objectMapper.writeValueAsString(tiltakstypeDto),
            )

            tiltakstypeRepository.get(tiltakstype.tiltakskode).getOrThrow() shouldBe tiltakstype
        }
    }
}
