package no.nav.amt.aktivitetskort.kafka.producer

import no.nav.amt.aktivitetskort.domain.Aktivitetskort
import no.nav.amt.aktivitetskort.kafka.consumer.AKTIVITETSKORT_TOPIC
import no.nav.amt.aktivitetskort.kafka.producer.dto.AktivitetskortKasseringPayload
import no.nav.amt.aktivitetskort.kafka.producer.dto.AktivitetskortPayload
import no.nav.amt.aktivitetskort.service.MetricsService
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Component
class AktivitetskortProducer(
    private val template: KafkaTemplate<String, String>,
    private val metricsService: MetricsService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun send(aktivitetskort: Aktivitetskort) = send(listOf(aktivitetskort))

    fun send(aktivitetskort: List<Aktivitetskort>) {
        aktivitetskort.forEach { currentAktivitetskort ->
            val messageId = UUID.randomUUID()
            val payload = AktivitetskortPayload(
                messageId = messageId,
                aktivitetskortType = currentAktivitetskort.tiltakstype,
                aktivitetskort = currentAktivitetskort.toAktivitetskortDto(),
            )

            template
                .send(
                    AKTIVITETSKORT_TOPIC,
                    currentAktivitetskort.id.toString(),
                    objectMapper.writeValueAsString(payload),
                ).get()

            log.info("Sendte aktivitetskort til aktivitetsplanen: ${currentAktivitetskort.id} messageId: $messageId")
            metricsService.incSendtAktivitetskort()
        }
    }

    fun slettAktivitetskort(
        aktivitetskortId: UUID,
        personIdent: String,
        navIdent: String,
    ) {
        val payload = AktivitetskortKasseringPayload(
            messageId = UUID.randomUUID(),
            aktivitetsId = aktivitetskortId,
            personIdent = personIdent,
            navIdent = navIdent,
            begrunnelse = "Kassering av duplikat aktivitetskort",
        )

        template
            .send(
                AKTIVITETSKORT_TOPIC,
                aktivitetskortId.toString(),
                objectMapper.writeValueAsString(payload),
            ).get()

        log.info("Slettet aktivitetskort: $aktivitetskortId")
    }
}
