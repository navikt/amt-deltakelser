package no.nav.amt.deltaker.enkeltplass.kafka

import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.enkeltplass.kafka.TotrinnskontrollHendelsePayload.TotrinnskontrollBesluttelse
import no.nav.amt.deltaker.enkeltplass.kafka.TotrinnskontrollHendelsePayload.TotrinnskontrollType
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.service.VedtakService
import no.nav.amt.deltaker.utils.DeltakerUtils
import no.nav.amt.deltaker.utils.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.utils.objectMapper
import org.slf4j.LoggerFactory
import tools.jackson.module.kotlin.readValue
import java.util.UUID

class TotrinnskontrollConsumer(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerService: DeltakerService,
    private val vedtakService: VedtakService,
) : Consumer<UUID, String?> {
    private val log = LoggerFactory.getLogger(javaClass)

    private val consumer = buildManagedKafkaConsumer(
        topic = Environment.TOTRINNSKONTROLL_TOPIC,
        skipFilter = { record ->
            // I dev inneholder offset 0-4 dårlig testdata fra produsenten som vi aldri vil prosessere.
            // Disse skippes uten å trigge retry. Filteret er bevisst begrenset til dev for å unngå
            // at vi ved et uhell hopper over gyldige meldinger i prod.
            Environment.isDev() && record.offset() < SKIP_RECORDS_BEFORE_OFFSET_IN_DEV
        },
        consumeFunc = ::consume,
    )

    suspend fun consume(
        key: UUID,
        value: String?,
    ) {
        if (value == null) {
            throw IllegalArgumentException("Tombstone er ikke støttet. Key: $key")
        }

        if (!skalBehandleTotrinnskontrollHendelse(value)) return

        val payload = objectMapper.readValue<TotrinnskontrollHendelsePayload>(value)

        if (payload.besluttelse == TotrinnskontrollBesluttelse.GODKJENT) {
            processGodkjentTotrinnskontroll(payload.entityId)
        }
    }

    internal fun processGodkjentTotrinnskontroll(gjennomforingId: UUID) {
        val deltaker = deltakerRepository
            .getEnkeltplassdeltaker(gjennomforingId)
            .getOrThrow()

        log.info("Behandler godkjent totrinnskontroll for deltaker ${deltaker.id}")

        if (deltaker.status.type != DeltakerStatus.Type.SOKT_INN) {
            log.warn("Deltaker ${deltaker.id} har status ${deltaker.status.type} og kan ikke godkjennes med totrinnskontroll.")
            return
        }

        deltakerService.upsertAndProduceDeltaker(
            deltaker = deltaker,
            erDeltakerSluttdatoEndret = false,
            beforeUpsert = { deltaker ->
                vedtakService.godkjentOkonomiFattVedtak(deltaker = deltaker)

                deltaker.copy(
                    status = DeltakerUtils.nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                )
            },
        )

        log.info("Totrinnskontrollhendelse behandlet for deltaker ${deltaker.id}")
    }

    internal fun skalBehandleTotrinnskontrollHendelse(payload: String): Boolean {
        // Parser kun ut type først – andre hendelsestyper enn ENKELTPLASS_OKONOMI kan ha
        // felter (f.eks. behandletAv) i et annet format enn vår modell, og skal uansett ignoreres.
        val typeName = objectMapper
            .readTree(payload)
            .get(TYPE_KEY)
            ?.asString()

        return if (typeName == TotrinnskontrollType.ENKELTPLASS_OKONOMI.name) {
            true
        } else {
            log.info("Totrinnskontrollhendelse av type $typeName ignorert")
            false
        }
    }

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()

    companion object {
        private const val SKIP_RECORDS_BEFORE_OFFSET_IN_DEV = 5L
        private const val TYPE_KEY = "type"
    }
}
