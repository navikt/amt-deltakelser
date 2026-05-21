import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.common.serialization.UUIDSerializer
import java.time.OffsetDateTime
import java.util.*

private const val DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092"
private const val DEFAULT_GJENNOMFORING_ENKELTPLASS_TOPIC = "team-mulighetsrommet.siste-tiltaksgjennomforinger-v2"

private val STATIC_ENKELTPLASS_GJENNOMFORING = GjennomforingV2KafkaPayload.Enkeltplass(
    id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
    opprettetTidspunkt = OffsetDateTime.parse("2025-01-01T00:00:00+00:00"),
    oppdatertTidspunkt = OffsetDateTime.parse("2025-01-01T00:00:00+00:00"),
    tiltakskode = Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING,
    arrangor = GjennomforingV2KafkaPayload.Arrangor(organisasjonsnummer = "924956704"),
    pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING,
    status = GjennomforingStatusType.GJENNOMFORES,
    oppstart = Oppstartstype.ENKELTPLASS,
    prisinformasjon = null,
)

class KafkaPublisher(
    bootstrapServers: String = getenvOrProperty("KAFKA_BOOTSTRAP_SERVERS", DEFAULT_BOOTSTRAP_SERVERS),
    private val gjennomforingEnkeltplassTopic: String = getenvOrProperty(
        "KAFKA_GJENNOMFORING_ENKELTPLASS_TOPIC",
        DEFAULT_GJENNOMFORING_ENKELTPLASS_TOPIC,
    ),
    private val producer: Producer<UUID, String> = createProducer(bootstrapServers),
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule()),
) {
    fun publishGjennomforingEnkeltplass() {
        val payload = STATIC_ENKELTPLASS_GJENNOMFORING
        val message = objectMapper.writeValueAsString(payload)
        producer.send(ProducerRecord(gjennomforingEnkeltplassTopic, payload.id, message)).get()
    }

    fun close() {
        producer.flush()
        producer.close()
    }
}

private fun createProducer(bootstrapServers: String): Producer<UUID, String> {
    val properties = Properties().apply {
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, UUIDSerializer::class.java.name)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.ACKS_CONFIG, "all")
        put(ProducerConfig.CLIENT_ID_CONFIG, "sim-nav")
    }

    return KafkaProducer(properties)
}

private fun getenvOrProperty(name: String, defaultValue: String): String {
    return System.getenv(name) ?: System.getProperty(name) ?: defaultValue
}
