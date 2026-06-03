package kafka

import tjenester.brreg.BronnoysundSimulator
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
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

private const val SISTE_TILTAKSGJENNOMFORINGER_TOPIC = "team-mulighetsrommet.siste-tiltaksgjennomforinger-v2"
private const val SISTE_TILTAKSTYPER_TOPIC = "team-mulighetsrommet.siste-tiltakstyper-v3"
private const val KAFKA_SERVER: String = "localhost:9092"

class KafkaPublisher(
    private val bronnoysundSimulator: tjenester.brreg.BronnoysundSimulator,
) {
    private val producer: Producer<UUID, String> = createProducer(KAFKA_SERVER)
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    fun publishGjennomforingEnkeltplass(
        payload: GjennomforingV2KafkaPayload.Enkeltplass = defaultGjennomforingEnkeltplass(),
    ) {
        val message = objectMapper.writeValueAsString(payload)
        producer.send(ProducerRecord(SISTE_TILTAKSGJENNOMFORINGER_TOPIC, payload.id, message)).get()
    }

    fun publishGjennomforingGruppe(
        payload: GjennomforingV2KafkaPayload.Gruppe = defaultGjennomforingGruppe(),
    ) {
        val message = objectMapper.writeValueAsString(payload)
        producer.send(ProducerRecord(SISTE_TILTAKSGJENNOMFORINGER_TOPIC, payload.id, message)).get()
    }

    fun publishTiltakstypeEnkeltplassArbeidsmarkedsopplaering(
        payload: TiltakstypeDto = defaultTiltakstypeEnkeltplassArbeidsmarkedsopplaering(),
    ) {
        val message = objectMapper.writeValueAsString(payload)
        producer.send(ProducerRecord(SISTE_TILTAKSTYPER_TOPIC, payload.id, message)).get()
    }

    fun defaultGjennomforingEnkeltplass(): GjennomforingV2KafkaPayload.Enkeltplass {
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        return GjennomforingV2KafkaPayload.Enkeltplass(
            id = UUID.randomUUID(),
            opprettetTidspunkt = now,
            oppdatertTidspunkt = now,
            tiltakskode = Tiltakskode.entries.first(),
            arrangor = GjennomforingV2KafkaPayload.Arrangor(organisasjonsnummer = bronnoysundSimulator.firstOrganisasjonsnummer()),
            pameldingType = GjennomforingPameldingType.entries.first(),
            status = GjennomforingStatusType.entries.first(),
            oppstart = Oppstartstype.entries.first(),
            prisinformasjon = null,
        )
    }

    fun defaultGjennomforingGruppe(): GjennomforingV2KafkaPayload.Gruppe {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val startDato = LocalDate.now(ZoneOffset.UTC)

        return GjennomforingV2KafkaPayload.Gruppe(
            id = UUID.randomUUID(),
            opprettetTidspunkt = now,
            oppdatertTidspunkt = now,
            tiltakskode = Tiltakskode.entries.first(),
            arrangor = GjennomforingV2KafkaPayload.Arrangor(organisasjonsnummer = bronnoysundSimulator.firstOrganisasjonsnummer()),
            pameldingType = GjennomforingPameldingType.entries.first(),
            status = GjennomforingStatusType.entries.first(),
            oppstart = Oppstartstype.entries.first(),
            navn = "Default gruppegjennomforing",
            startDato = startDato,
            sluttDato = startDato.plusDays(30),
            tilgjengeligForArrangorFraOgMedDato = null,
            apentForPamelding = true,
            antallPlasser = 10,
            deltidsprosent = 100.0,
            oppmoteSted = null,
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


