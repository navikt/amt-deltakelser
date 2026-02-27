package no.nav.amt.lib.kafka

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.lib.kafka.KafkaTestUtils.TOPIC_IN_TEST
import no.nav.amt.lib.kafka.KafkaTestUtils.intConsumerConfig
import no.nav.amt.lib.kafka.KafkaTestUtils.produceIntInt
import no.nav.amt.lib.kafka.KafkaTestUtils.produceStringString
import no.nav.amt.lib.kafka.KafkaTestUtils.produceUUIDByteArray
import no.nav.amt.lib.kafka.KafkaTestUtils.stringConsumerConfig
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.amt.lib.testing.SingletonKafkaProvider
import no.nav.amt.lib.testing.eventually
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.UUIDDeserializer
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

class ManagedKafkaConsumerTest {
    @Test
    fun `ManagedKafkaConsumer - konsumerer record med String, String`() {
        val key = "key"
        val value = "value"
        val cache = mutableMapOf<String, String>()

        produceStringString(ProducerRecord(TOPIC_IN_TEST, key, value))

        val consumer = ManagedKafkaConsumer(TOPIC_IN_TEST, stringConsumerConfig) { k: String, v: String ->
            cache[k] = v
        }
        consumer.start()

        eventually {
            cache[key] shouldBe value
        }

        runBlocking { consumer.close() }
    }

    @Test
    fun `ManagedKafkaConsumer - konsumerer record med UUID, ByteArray`() {
        val key = UUID.randomUUID()
        val value = "value".toByteArray()
        val cache = mutableMapOf<UUID, ByteArray>()
        val uuidTopic = "uuid.topic"

        produceUUIDByteArray(ProducerRecord(uuidTopic, key, value))

        val config = LocalKafkaConfig(SingletonKafkaProvider.getHost())
            .consumerConfig(
                keyDeserializer = UUIDDeserializer(),
                valueDeserializer = ByteArrayDeserializer(),
                groupId = "test-consumer-${UUID.randomUUID()}",
            )

        val consumer = ManagedKafkaConsumer(uuidTopic, config) { k: UUID, v: ByteArray ->
            cache[k] = v
        }
        consumer.start()

        eventually {
            cache[key] shouldBe value
        }

        runBlocking { consumer.close() }
    }

    @Test
    fun `ManagedKafkaConsumer - prøver å konsumere melding på nytt hvis noe feiler`() {
        produceStringString(ProducerRecord(TOPIC_IN_TEST, "~key1~", "~value~"))
        produceStringString(ProducerRecord(TOPIC_IN_TEST, "~key2~", "~value~"))

        var numberOfInvocations = 0
        val failOnceKeys = mutableSetOf("~key2~")

        val consumer = ManagedKafkaConsumer<String, String>(TOPIC_IN_TEST, stringConsumerConfig) { key, _ ->
            numberOfInvocations++

            if (key in failOnceKeys) {
                failOnceKeys.remove(key)
                error("Should retry")
            }
        }

        consumer.start()

        eventually {
            numberOfInvocations shouldBe 3
        }

        runBlocking { consumer.close() }
    }

    @Test
    fun `ManagedKafkaConsumer - konsumerer mange meldinger med feil og flere partisjoner`() {
        val intTopic = NewTopic("int.topic-1", 4, 1)
        SingletonKafkaProvider.adminClient
            .createTopics(listOf(intTopic))
            .all()
            .get()

        val data = (1..500).toList()
        val consumed = mutableListOf<Int>()
        val failures = mutableListOf(7, 42, 42, 333)

        val consumer = ManagedKafkaConsumer<Int, Int>(intTopic.name(), intConsumerConfig) { k, _ ->
            if (k in failures) {
                failures.remove(k)
                error("Skal feile på $k")
            }
            consumed.add(k)
        }

        consumer.start()

        data.forEach {
            val partition = (0..3).random()
            produceIntInt(ProducerRecord(intTopic.name(), partition, it, it))
        }

        eventually(Duration.ofSeconds(15)) {
            consumed.size shouldBe data.size
            consumed.toSet().size shouldBe data.size
        }
    }

    @Test
    fun `ManagedKafkaConsumer - konsumerer mange meldinger med samme key i riktig rekkefølge`() {
        val intTopic = NewTopic("int.topic-2", 4, 1)
        SingletonKafkaProvider.adminClient
            .createTopics(listOf(intTopic))
            .all()
            .get()

        val keys = (1..50).toList()
        val firstValue = 1
        val lastValue = 2
        val data = keys.map { Pair(it, firstValue) } + keys.map { Pair(it, lastValue) }

        val consumed = mutableMapOf<Int, Int>()
        val failures = mutableListOf(7, 42, 42, 333)
        val consumer = ManagedKafkaConsumer<Int, Int>(intTopic.name(), intConsumerConfig) { k, v ->
            if (k in failures) {
                failures.remove(k)
                error("Skal feile på $k")
            }
            if (v == lastValue) {
                consumed[k] shouldBe firstValue
            }
            consumed[k] = v
        }

        consumer.start()

        data.forEach {
            val partition = it.first % intTopic.numPartitions()
            produceIntInt(ProducerRecord(intTopic.name(), partition, it.first, it.second))
        }

        eventually(Duration.ofSeconds(15)) {
            consumed.values.toSet() shouldBe setOf(lastValue)
        }
    }
}
