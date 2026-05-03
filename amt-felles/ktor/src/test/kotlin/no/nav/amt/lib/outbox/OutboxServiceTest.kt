package no.nav.amt.lib.outbox

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.amt.lib.outbox.metrics.PrometheusOutboxMeter
import no.nav.amt.lib.testing.TestPostgresContainer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.UUID

data class Nested(
    val inner: String,
)

data class LargeValue(
    val list: List<Int>,
    val nested: Nested,
)

class OutboxServiceTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setupAll() = TestPostgresContainer.bootstrap()
    }

    private val prometheusRegistry = PrometheusRegistry()
    private val repository: OutboxRepository = OutboxRepository()
    private val service: OutboxService = OutboxService(PrometheusOutboxMeter(prometheusRegistry))

    data class TestValue(
        val foo: String,
        val bar: Int,
    )

    @Test
    fun `insertRecord creates and persists an record with correct fields`() {
        val valueInTest = TestValue("hello", 42)
        val keyInTest = UUID.randomUUID()
        val topicInTest = "test-topic"

        val record = service.insertRecord(keyInTest, valueInTest, topicInTest)
        assertSoftly(record) {
            id shouldNotBe null
            key shouldBe keyInTest.toString()
            valueType shouldBe TestValue::class.simpleName
            topic shouldBe topicInTest
            value["foo"].asString() shouldBe valueInTest.foo
            value["bar"].asInt() shouldBe valueInTest.bar
        }

        val persisted = repository.get(record.id)
        assertSoftly(persisted.shouldNotBeNull()) {
            key shouldBe keyInTest.toString()
            valueType shouldBe TestValue::class.simpleName
            topic shouldBe topicInTest
            value.get("foo")?.asString() shouldBe valueInTest.foo
            value.get("bar")?.asInt() shouldBe valueInTest.bar
        }
    }

    @Test
    fun `insertRecord handles special characters in value fields`() {
        data class SpecialCharValue(
            val foo: String,
        )

        val specialString = "Hello, 世界! \"quotes\" \n new line"
        val value = SpecialCharValue(specialString)
        val key = UUID.randomUUID()
        val topic = "special-char-topic"

        val record = service.insertRecord(key, value, topic)
        record.value["foo"].asString() shouldBe specialString
    }

    @Test
    fun `insertRecord works with large and nested values`() {
        val value = LargeValue(List(1000) { it }, Nested("deep"))
        val key = UUID.randomUUID()
        val topic = "large-topic"

        val record = service.insertRecord(key, value, topic)
        record.value["list"].size() shouldBe 1000
        record.value["nested"]["inner"].asString() shouldBe "deep"
    }
}
