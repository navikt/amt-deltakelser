package no.nav.amt.deltaker.navtiltakskoordinator.ulestdeltakerhendelse

import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.navtiltakskoordinator.ulestdeltakerhendelse.extensions.toUlestHendelse
import no.nav.amt.deltaker.utils.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.hendelse.Hendelse
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.utils.objectMapper
import org.slf4j.LoggerFactory
import tools.jackson.module.kotlin.readValue
import java.util.UUID

class UlestHendelseConsumer(
    private val repository: UlestHendelseRepository,
) : Consumer<UUID, String?> {
    private val log = LoggerFactory.getLogger(javaClass)

    private val consumer = buildManagedKafkaConsumer(
        topic = Environment.DELTAKER_HENDELSE_TOPIC,
        consumeFunc = ::consume,
    )

    suspend fun consume(
        key: UUID,
        value: String?,
    ) {
        if (value == null) {
            log.warn("Mottok tombstone for melding med id: $key")
            repository.delete(key)
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
            -> repository.upsert(hendelse.toUlestHendelse()!!)

            else -> Unit
        }
    }

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()
}
