package no.nav.amt.deltaker.bff.tiltak

import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.lib.models.deltaker.toV2
import no.nav.amt.lib.models.deltakerliste.tiltakstype.TiltakstypeSystem
import no.nav.amt.lib.models.deltakerliste.tiltakstype.kafka.TiltakstypeDto
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class TiltakConsumerTest {
    private val tiltakstype = TestData.lagTiltakstype()
    private val tiltakRepository = TiltakRepository()
    private val sut = TiltakConsumer(tiltakRepository)

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `consumeTiltakstype - ny, aktiv tiltakstype - lagrer tiltakstype`() {
        val tiltakstypeDto = TiltakstypeDto(
            id = tiltakstype.id,
            navn = tiltakstype.navn,
            tiltakskode = tiltakstype.tiltakskode,
            innsatsgrupper = tiltakstype.innsatsgrupper.map { it.toV2() }.toSet(),
            deltakerRegistreringInnhold = tiltakstype.innhold,
        )

        runTest {
            sut.consume(
                tiltakstype.id,
                objectMapper.writeValueAsString(tiltakstypeDto),
            )

            tiltakRepository.get(tiltakstype.tiltakskode).shouldBeSuccess() shouldBe tiltakstype
        }
    }

    @Test
    fun `consumeTiltakstype - TiltakstypeSystem TILTAKSADMINISTRASJON - lagrer tiltakstype`() {
        val tiltakstypeDto = TiltakstypeDto(
            id = tiltakstype.id,
            navn = tiltakstype.navn,
            tiltakskode = tiltakstype.tiltakskode,
            system = TiltakstypeSystem.TILTAKSADMINISTRASJON,
            innsatsgrupper = tiltakstype.innsatsgrupper.map { it.toV2() }.toSet(),
            deltakerRegistreringInnhold = tiltakstype.innhold,
        )

        runTest {
            sut.consume(
                tiltakstype.id,
                objectMapper.writeValueAsString(tiltakstypeDto),
            )

            tiltakRepository.get(tiltakstype.tiltakskode).shouldBeSuccess() shouldBe tiltakstype
        }
    }

    @Test
    fun `consumeTiltakstype - TiltakstypeSystem ARENA - lagrer ikke tiltakstype`() {
        val tiltakstypeDto = TiltakstypeDto(
            id = tiltakstype.id,
            navn = tiltakstype.navn,
            tiltakskode = tiltakstype.tiltakskode,
            system = TiltakstypeSystem.ARENA,
            innsatsgrupper = tiltakstype.innsatsgrupper.map { it.toV2() }.toSet(),
            deltakerRegistreringInnhold = tiltakstype.innhold,
        )
        runTest {
            sut.consume(
                tiltakstype.id,
                objectMapper.writeValueAsString(tiltakstypeDto),
            )

            tiltakRepository.get(tiltakstype.tiltakskode).shouldBeFailure()
        }
    }
}
