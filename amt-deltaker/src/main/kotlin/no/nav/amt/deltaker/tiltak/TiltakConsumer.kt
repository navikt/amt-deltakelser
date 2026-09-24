package no.nav.amt.deltaker.tiltak

import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.kafka.AmtGjennomforingProducer
import no.nav.amt.deltaker.kafka.toAmtGjennomforingPayload
import no.nav.amt.deltaker.repository.DeltakerlisteRepository
import no.nav.amt.deltaker.utils.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskoder.skalKometLagreTiltakstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.models.kafka.TiltakstypePayload
import no.nav.amt.lib.utils.objectMapper
import org.slf4j.LoggerFactory
import tools.jackson.module.kotlin.readValue
import java.util.UUID

class TiltakConsumer(
    private val repository: TiltakRepository,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val amtGjennomforingProducer: AmtGjennomforingProducer,
) : Consumer<UUID, String?> {
    private val log = LoggerFactory.getLogger(javaClass)

    private val consumer = buildManagedKafkaConsumer(
        topic = Environment.TILTAKSTYPE_TOPIC,
        consumeFunc = ::consume,
    )

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()

    fun consume(
        key: UUID,
        value: String?,
    ) {
        if (value == null || !skalKometLagreTiltakstype(value, objectMapper)) {
            return
        }

        val tiltakstypePayload = objectMapper.readValue<TiltakstypePayload>(value)
        val nyTiltakstype = tiltakstypePayload.toModel()

        val eksisterendeTiltakstype = repository.get(nyTiltakstype.tiltakskode).getOrNull()
        repository.upsert(nyTiltakstype)

        if (eksisterendeTiltakstype != null && paavirkerGjennomforingPayload(eksisterendeTiltakstype, nyTiltakstype)) {
            reproduserGjennomforinger(nyTiltakstype.id)
        }
    }

    /**
     * Deler alle gjennomføringer som peker på tiltakstypen på nytt, slik at den embeddede tiltakstypen
     * i `amt.gjennomforing-intern`-payloaden blir oppdatert hos konsumentene.
     */
    private fun reproduserGjennomforinger(tiltakstypeId: UUID) {
        val gjennomforinger = deltakerlisteRepository
            .getManyForTiltakstype(tiltakstypeId)
            .filter { it.arrangor != null }

        gjennomforinger.forEach { amtGjennomforingProducer.produce(it.toAmtGjennomforingPayload()) }

        log.info("Reproduserte ${gjennomforinger.size} gjennomføringer etter endring i tiltakstype $tiltakstypeId")
    }

    /**
     * Kun feltene som deles i [no.nav.amt.lib.models.kafka.AmtGjennomforingPayload.Tiltak] utløser
     * reproduksjon; endringer i f.eks. innsatsgrupper påvirker ikke payloaden.
     */
    private fun paavirkerGjennomforingPayload(
        eksisterende: Tiltakstype,
        ny: Tiltakstype,
    ): Boolean = eksisterende.navn != ny.navn || eksisterende.innhold != ny.innhold
}
