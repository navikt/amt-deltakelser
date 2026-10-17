package no.nav.amt.distribusjon.varsel.hendelse

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.distribusjon.Environment
import no.nav.amt.distribusjon.varsel.VarselRepository
import no.nav.amt.lib.kafka.config.KafkaConfig
import no.nav.amt.lib.utils.objectMapper
import no.nav.tms.varsel.action.Varseltype
import org.junit.jupiter.api.Test
import java.util.UUID

class VarselHendelseConsumerTest {
    @Test
    fun `databasefeil ved oppslag av varsel kastes videre`() = runTest {
        // Arrange
        val varselId = UUID.randomUUID()
        val databaseFeil = IllegalStateException("Database er utilgjengelig")
        val repository = mockk<VarselRepository>()
        every { repository.get(varselId) } returns Result.failure(databaseFeil)
        val consumer = VarselHendelseConsumer(
            varselRepository = repository,
            varselService = mockk(relaxed = true),
            kafkaConfig = mockk<KafkaConfig>(relaxed = true),
        )
        val hendelse = InaktivertVarselHendelse(
            varselId = varselId.toString(),
            varseltype = Varseltype.Beskjed,
            namespace = Environment.namespace,
            appnavn = Environment.appName,
        )

        // Act
        val kastetFeil = shouldThrow<IllegalStateException> {
            consumer.consume(varselId.toString(), objectMapper.writeValueAsString(hendelse))
        }

        // Assert
        kastetFeil.message shouldBe databaseFeil.message
        verify(exactly = 1) { repository.get(varselId) }
    }
}
