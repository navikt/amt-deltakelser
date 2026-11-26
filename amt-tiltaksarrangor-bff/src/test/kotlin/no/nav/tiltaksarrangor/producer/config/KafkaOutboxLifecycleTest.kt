package no.nav.tiltaksarrangor.producer.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import no.nav.common.kafka.producer.feilhandtering.KafkaProducerRecordProcessor
import org.junit.jupiter.api.Test

class KafkaOutboxLifecycleTest {
    private val processor = mockk<KafkaProducerRecordProcessor>(relaxUnitFun = true)
    private val lifecycle = KafkaOutboxLifecycle(processor)

    @Test
    fun `bruker bekreftet lukking ved stopp`() {
        lifecycle.start()
        lifecycle.stop()

        lifecycle.isRunning shouldBe false
        verifyOrder {
            processor.start()
            processor.closeAndAwait()
        }
    }

    @Test
    fun `setter status til stoppet selv om lukking feiler`() {
        val failure = IllegalStateException("Simulert lukkefeil")
        every { processor.closeAndAwait() } throws failure
        lifecycle.start()

        shouldThrow<IllegalStateException> { lifecycle.stop() } shouldBe failure

        lifecycle.isRunning shouldBe false
    }
}
