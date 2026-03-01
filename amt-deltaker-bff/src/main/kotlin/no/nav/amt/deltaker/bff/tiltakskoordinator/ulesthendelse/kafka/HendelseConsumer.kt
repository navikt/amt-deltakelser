package no.nav.amt.deltaker.bff.tiltakskoordinator.ulesthendelse.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.tiltakskoordinator.ulesthendelse.UlestHendelseService
import no.nav.amt.deltaker.bff.utils.KafkaConsumerFactory.AUTO_OFFSET_RESET_LATEST
import no.nav.amt.deltaker.bff.utils.KafkaConsumerFactory.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.hendelse.Hendelse
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.utils.objectMapper
import org.slf4j.LoggerFactory
import java.util.UUID

class HendelseConsumer(
    private val ulestHendelseService: UlestHendelseService,
) : Consumer<UUID, String?> {
    private val log = LoggerFactory.getLogger(javaClass)

    private val consumer = buildManagedKafkaConsumer(
        topic = Environment.DELTAKER_HENDELSE_TOPIC,
        kafkaAutoOffsetReset = AUTO_OFFSET_RESET_LATEST,
        consumeFunc = ::consume,
    )

    suspend fun consume(key: UUID, value: String?) {
        if (value == null) {
            log.warn("Mottok tombstone for melding med id: $key")
            ulestHendelseService.delete(key)
            return
        }
        val hendelse = objectMapper.readValue<Hendelse>(value)

        if (hendelse.deltaker.deltakerliste.pameldingstype == GjennomforingPameldingType.DIREKTE_VEDTAK) {
            return
        }

        when (hendelse.payload) {
            is HendelseType.InnbyggerGodkjennUtkast,
            is HendelseType.NavGodkjennUtkast,
            is HendelseType.IkkeAktuell,
            is HendelseType.AvsluttDeltakelse,
            is HendelseType.AvbrytDeltakelse,
            is HendelseType.ReaktiverDeltakelse,
            -> ulestHendelseService.lagreUlestHendelse(hendelse)

            else -> Unit
        }
    }

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()
}
