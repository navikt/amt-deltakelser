package no.nav.amt.deltaker.enkeltplass.kafka

import no.nav.amt.deltaker.Environment
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
import java.time.LocalDate
import java.util.UUID

/**
 * Konsumerer totrinnskontrollhendelser for enkeltplass fra Kafka.
 *
 * Konsumenten filtrerer på relevante hendelser, ignorerer irrelevante typer,
 * og behandler godkjente økonomihendelser ved å oppdatere deltaker og vedtak.
 *
 * I dev-miljø brukes et `skipFilter` for å hoppe over kjente ugyldige meldinger
 * på lave offsets uten å trigge retry.
 *
 * @param deltakerRepository repository for oppslag av enkeltplassdeltakere
 * @param deltakerService tjeneste for oppdatering og publisering av deltaker
 * @param vedtakService tjeneste for å fatte vedtak ved godkjent økonomi
 */
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

    /**
     * Behandler en melding fra Kafka.
     *
     * Kaster feil ved tombstone, filtrerer bort irrelevante hendelser,
     * og prosesserer kun godkjente ENKELTPLASS_OKONOMI-hendelser.
     *
     * @param key Kafka-key for meldingen
     * @param value rå payload fra Kafka (kan være `null` ved tombstone)
     */
    suspend fun consume(
        key: UUID,
        value: String?,
    ) {
        if (value == null) {
            throw IllegalArgumentException("Tombstone er ikke støttet. Key: $key")
        }

        if (!skalBehandleTotrinnskontrollHendelse(value)) return

        val payload = objectMapper.readValue<TotrinnskontrollHendelsePayload>(value)

        if (payload.status == TotrinnskontrollHendelsePayload.Status.GODKJENT &&
            payload.type == TotrinnskontrollType.ENKELTPLASS_OKONOMI
        ) {
            processGodkjentInnsoking(payload.entityId)
        }

/* For bruk senere
    if (payload.type == TotrinnskontrollType.ENKELTPLASS_PRISENDRING) {
            processGodkjentPrisinformasjon(payload)
        }*/
    }

    internal fun processGodkjentPrisinformasjon(totrinnskontrollPayload: TotrinnskontrollHendelsePayload) {
        // Finn forslaget med riktig totrinnskontrollId for å garantere at der er riktig prisionfo
        if (totrinnskontrollPayload.status == TotrinnskontrollHendelsePayload.Status.TIL_BEHANDLING) {
            // sendes automatisk når valp har mottatt meldingen
        }
        // Finn forslaget med riktig totrinnskontrollId for å garantere at der er riktig prisionfo

        /*
            if(deltakelse allerede godkjent/økonomi allerede har blitt godkjent) {
                //Generer endringsvedtak for prisendring
                //Godkjenn forslag
            }
            else {
                //prisendringer blir automatisk godkjent når deltakelsen er bare søkt inn
                oppdater deltaker med ny prisinformasjon
                ikke lage vedtak
                godkjenne forslag(skal det vises for veileder?)
            }
         */
    }

    /**
     * Prosesserer godkjent totrinnskontroll for en enkeltplassdeltaker.
     *
     * Kun deltakere med status `SOKT_INN` behandles. Ved behandling fattes vedtak,
     * og deltaker settes til:
     * - `VENTER_PA_OPPSTART` når startdato er i fremtiden
     * - `DELTAR` når startdato er i dag eller fortid og sluttdato er i fremtiden
     * - `FULLFORT` når startdato og sluttdato er i fortid
     *
     * @param gjennomforingId id for gjennomføringen som brukes til å finne deltaker
     */
    internal fun processGodkjentInnsoking(gjennomforingId: UUID) {
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
                // TODO: Generer melding om hovedvedtak til amt-distribusjon
                val idag = LocalDate.now()
                val nyStatusType = when {
                    deltaker.startdato != null &&
                        deltaker.sluttdato != null &&
                        deltaker.startdato.isBefore(idag) &&
                        deltaker.sluttdato.isBefore(idag) ->
                        DeltakerStatus.Type.FULLFORT

                    deltaker.startdato != null && deltaker.startdato.isAfter(idag) ->
                        DeltakerStatus.Type.VENTER_PA_OPPSTART

                    else ->
                        DeltakerStatus.Type.DELTAR
                }
                deltaker.copy(
                    status = DeltakerUtils.nyDeltakerStatus(nyStatusType),
                )
            },
        )

        log.info("Totrinnskontrollhendelse behandlet for deltaker ${deltaker.id}")
    }

    /**
     * Returnerer `true` når payload er en ENKELTPLASS_OKONOMI-hendelse som skal behandles.
     *
     * Metoden leser kun ut feltet `type` for å kunne ignorere hendelser med annen
     * struktur uten å feile deserialisering av resten av payload.
     *
     * @param payload rå JSON-payload fra Kafka
     */
    internal fun skalBehandleTotrinnskontrollHendelse(payload: String): Boolean {
        // Parser kun ut type først – andre hendelsestyper enn ENKELTPLASS_OKONOMI kan ha
        // felter (f.eks. behandletAv) i et annet format enn vår modell, og skal uansett ignoreres.
        val typeName = objectMapper
            .readTree(payload)
            .get(TYPE_KEY)
            ?.asString()

        return when (typeName) {
            TotrinnskontrollType.ENKELTPLASS_OKONOMI.name -> {
                // Søkt inn deltakelse godkjent
                true
            }

            TotrinnskontrollType.ENKELTPLASS_PRISENDRING.name -> {
                // Godkjent prisendring for deltakelse
                true
            }

            else -> {
                log.info("Totrinnskontrollhendelse av type $typeName ignorert")
                false
            }
        }
    }

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()

    companion object {
        private const val SKIP_RECORDS_BEFORE_OFFSET_IN_DEV = 5L
        private const val TYPE_KEY = "type"
    }
}
