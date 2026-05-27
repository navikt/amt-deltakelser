package kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.amt.lib.models.deltaker.InnsatsgruppeV2
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.deltakerliste.tiltakstype.kafka.TiltakstypeDto
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.common.serialization.UUIDSerializer
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

private const val DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092"
private const val DEFAULT_GJENNOMFORING_ENKELTPLASS_TOPIC = "team-mulighetsrommet.siste-tiltaksgjennomforinger-v2"
private const val DEFAULT_TILTAKSTYPE_TOPIC = "team-mulighetsrommet.siste-tiltakstyper-v3"
private const val BRONNOYSUND_DATA_PATH = "/bronnoysund-data.json"

private val DEFAULT_ARRANGOR_ORGNR: String = loadDefaultArrangorOrgnr()

class KafkaPublisher(
    bootstrapServers: String = getenvOrProperty("KAFKA_BOOTSTRAP_SERVERS", DEFAULT_BOOTSTRAP_SERVERS),
    private val gjennomforingEnkeltplassTopic: String = getenvOrProperty(
        "KAFKA_GJENNOMFORING_ENKELTPLASS_TOPIC",
        DEFAULT_GJENNOMFORING_ENKELTPLASS_TOPIC,
    ),
    private val tiltakstypeTopic: String = getenvOrProperty(
        "KAFKA_TILTAKSTYPE_TOPIC",
        DEFAULT_TILTAKSTYPE_TOPIC,
    ),
    private val producer: Producer<UUID, String> = createProducer(bootstrapServers),
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule()),
) {
    fun publishGjennomforingEnkeltplass(
        payload: GjennomforingV2KafkaPayload.Enkeltplass = defaultGjennomforingEnkeltplass(),
    ) {
        val message = objectMapper.writeValueAsString(payload)
        producer.send(ProducerRecord(gjennomforingEnkeltplassTopic, payload.id, message)).get()
    }

    fun publishTiltakstypeEnkeltplassArbeidsmarkedsopplaering(
        payload: TiltakstypeDto = defaultTiltakstypeEnkeltplassArbeidsmarkedsopplaering(),
    ) {
        val message = objectMapper.writeValueAsString(payload)
        producer.send(ProducerRecord(tiltakstypeTopic, payload.id, message)).get()
    }

    fun defaultGjennomforingEnkeltplass(): GjennomforingV2KafkaPayload.Enkeltplass {
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        return GjennomforingV2KafkaPayload.Enkeltplass(
            id = UUID.randomUUID(),
            opprettetTidspunkt = now,
            oppdatertTidspunkt = now,
            tiltakskode = Tiltakskode.entries.first(),
            arrangor = GjennomforingV2KafkaPayload.Arrangor(organisasjonsnummer = DEFAULT_ARRANGOR_ORGNR),
            pameldingType = GjennomforingPameldingType.entries.first(),
            status = GjennomforingStatusType.entries.first(),
            oppstart = Oppstartstype.entries.first(),
            prisinformasjon = null,
        )
    }

    fun defaultTiltakstypeEnkeltplassArbeidsmarkedsopplaering(): TiltakstypeDto {
        return TiltakstypeDto(
            id = UUID.randomUUID(),
            navn = "Default tiltakstype",
            tiltakskode = Tiltakskode.entries.first(),
            innsatsgrupper = setOf(InnsatsgruppeV2.entries.first()),
            deltakerRegistreringInnhold = null,
        )
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

private fun loadDefaultArrangorOrgnr(): String {
    val stream = object {}.javaClass.getResourceAsStream(BRONNOYSUND_DATA_PATH)
        ?: throw IllegalStateException("Missing resource: $BRONNOYSUND_DATA_PATH")

    return stream.use {
        val root = jacksonObjectMapper().readTree(it)
        val enheter = root.path("enheter")
        val organisasjonsnummer = enheter.takeIf { it.isArray && it.size() > 0 }
            ?.get(0)
            ?.path("organisasjonsnummer")
            ?.asText()
            ?.takeIf { it.isNotBlank() }

        organisasjonsnummer ?: throw IllegalStateException(
            "Missing first enheter.organisasjonsnummer in resource: $BRONNOYSUND_DATA_PATH",
        )
    }
}

