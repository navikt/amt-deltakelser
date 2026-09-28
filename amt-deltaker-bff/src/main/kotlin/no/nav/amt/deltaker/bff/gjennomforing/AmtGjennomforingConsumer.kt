package no.nav.amt.deltaker.bff.gjennomforing

import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.SelfServiceTilgangService
import no.nav.amt.deltaker.bff.tiltak.TiltakRepository
import no.nav.amt.deltaker.bff.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.bff.utils.KafkaConsumerFactory
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.kafka.AmtGjennomforingPayload
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import tools.jackson.module.kotlin.readValue
import java.util.UUID

/**
 * Konsumerer det interne topicet `amt.gjennomforing-intern` (produsert av amt-deltaker) og
 * populerer `tiltakstype`- og `deltakerliste`-tabeller.
 **/
class AmtGjennomforingConsumer(
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val tiltakRepository: TiltakRepository,
    private val arrangorService: ArrangorService,
    private val selfServiceTilgangService: SelfServiceTilgangService,
) : Consumer<UUID, String?> {
    private val consumer = KafkaConsumerFactory.buildManagedKafkaConsumer(
        topic = Environment.AMT_GJENNOMFORING_INTERN_TOPIC,
        consumerGroupId = Environment.KAFKA_CONSUMER_GROUP_ID + "gjennomforing-intern",
        consumeFunc = ::consume,
    )

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()

    suspend fun consume(
        key: UUID,
        value: String?,
    ) {
        if (value == null) {
            deltakerlisteRepository.delete(key)
        } else {
            handleGjennomforing(objectMapper.readValue(value))
        }
    }

    private suspend fun handleGjennomforing(payload: AmtGjennomforingPayload) {
        val tiltak = payload.toTiltak()
        val arrangor = arrangorService.hentArrangor(payload.arrangor.organisasjonsnummer)
        val deltakerliste = payload.toDeltakerliste(arrangor)

        Database.transaction {
            tiltakRepository.upsert(tiltak)
            deltakerlisteRepository.upsert(deltakerliste, tiltak.id)

            if (deltakerliste.status == GjennomforingStatusType.AVLYST || deltakerliste.status == GjennomforingStatusType.AVBRUTT) {
                selfServiceTilgangService.stengTilgangerTilDeltakerliste(deltakerliste.id)
            }
        }
    }
}
