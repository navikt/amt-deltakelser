package no.nav.amt.lib.kafka

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.kafka.KafkaTestUtils.topicPartition1
import no.nav.amt.lib.kafka.KafkaTestUtils.topicPartition2
import no.nav.amt.lib.testing.eventually
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PartitionBackoffManagerTest {
    private lateinit var sut: PartitionBackoffManager

    @BeforeEach
    fun setup() {
        sut = PartitionBackoffManager()
    }

    @Test
    fun `incrementRetryCount håndterer flere partisjoner uavhengig`() {
        sut.incrementRetryCount(topicPartition1)
        sut.incrementRetryCount(topicPartition2)
        sut.incrementRetryCount(topicPartition2)

        sut.getRetryCount(topicPartition1) shouldBe 1
        sut.getRetryCount(topicPartition2) shouldBe 2
    }

    @Test
    fun `resetRetryCount pa en uregistrert partisjon kaster ikke feil`() {
        shouldNotThrowAny {
            sut.resetRetryCount(topicPartition1)
        }
    }

    @Nested
    inner class IsInBackoffTests {
        @Test
        fun `skal returnere false hvis partisjon ikke er i backoff`() {
            sut.isInBackoff(topicPartition1) shouldBe false
        }

        @Test
        fun `skal returnere true etter incrementRetryCount`() {
            sut.incrementRetryCount(topicPartition1)
            sut.isInBackoff(topicPartition1) shouldBe true
        }

        @Test
        fun `skal returnere false etter at backoff er over`() {
            sut.incrementRetryCount(topicPartition1)
            sut.isInBackoff(topicPartition1) shouldBe true

            eventually {
                sut.isInBackoff(topicPartition1) shouldBe false
            }
        }

        @Test
        fun `skal returnere false etter resetRetryCount`() {
            sut.incrementRetryCount(topicPartition1)
            sut.isInBackoff(topicPartition1) shouldBe true

            sut.resetRetryCount(topicPartition1)
            sut.isInBackoff(topicPartition1) shouldBe false
        }
    }

    @Nested
    inner class BackoffTimingTests {
        @Test
        fun `backoff varer minst BASE_DELAY`() {
            sut.incrementRetryCount(topicPartition1)

            val backoffUntil = sut.calculateBackoffUntilMs(1)

            backoffUntil - System.currentTimeMillis() shouldBeGreaterThanOrEqual 1_000L
        }

        @Test
        fun `store retryCounts gir MAX_DELAY`() {
            val backoffUntil = sut.calculateBackoffUntilMs(10_000)

            backoffUntil - System.currentTimeMillis() shouldBeLessThanOrEqual PartitionBackoffManager.MAX_DELAY
        }

        @Test
        fun `calculateBackoffUntilMs aldri storre enn MAX_DELAY`() {
            val backoff = sut.calculateBackoffUntilMs(1000)
            val now = System.currentTimeMillis()

            backoff - now shouldBeLessThanOrEqual PartitionBackoffManager.MAX_DELAY
        }

        @Test
        fun `calculateBackoffUntilMs vokser med retryCount`() {
            val backoff1 = sut.calculateBackoffUntilMs(1)
            val backoff2 = sut.calculateBackoffUntilMs(2)

            backoff2 shouldBeGreaterThan backoff1
        }
    }
}
