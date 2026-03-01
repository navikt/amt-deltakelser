package no.nav.amt.deltaker.bff.deltaker.forslag

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.bff.deltaker.forslag.kafka.ArrangorMeldingConsumer
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.deltaker.bff.utils.data.TestRepository
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime
import java.util.UUID

class ArrangorMeldingConsumerTest {
    val forslagRepository = ForslagRepository()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `consume - forslag VenterPaSvar - lagrer`(): Unit = runBlocking {
        val consumer = ArrangorMeldingConsumer(forslagRepository)
        val deltaker = TestData.lagDeltaker()
        TestRepository.insert(deltaker)
        val forslag = TestData.lagForslag(deltakerId = deltaker.id)

        consumer.consume(
            forslag.id,
            objectMapper.writeValueAsString(forslag),
        )

        val forslagFraDb = forslagRepository.getForDeltaker(deltaker.id)
        forslagFraDb.size shouldBe 1
        forslagFraDb.first().id shouldBe forslag.id
    }

    @Test
    fun `consume - forslag tilbakekalt - sletter`(): Unit = runBlocking {
        val consumer = ArrangorMeldingConsumer(forslagRepository)
        val deltaker = TestData.lagDeltaker()
        TestRepository.insert(deltaker)
        val forslag = TestData.lagForslag(deltakerId = deltaker.id)
        forslagRepository.upsert(forslag)

        consumer.consume(
            forslag.id,
            objectMapper.writeValueAsString(
                forslag.copy(
                    status = Forslag.Status.Tilbakekalt(UUID.randomUUID(), LocalDateTime.now()),
                ),
            ),
        )

        val forslagFraDb = forslagRepository.getForDeltaker(deltaker.id)
        forslagFraDb.size shouldBe 0
    }
}
