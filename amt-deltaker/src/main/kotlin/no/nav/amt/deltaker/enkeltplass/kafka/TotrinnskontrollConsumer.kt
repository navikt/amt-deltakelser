package no.nav.amt.deltaker.enkeltplass.kafka

import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.enkeltplass.kafka.TotrinnskontrollHendelsePayload.TotrinnskontrollType
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.repository.PrisinfoRepository
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.service.DistribuerEndringService
import no.nav.amt.deltaker.service.VedtakService
import no.nav.amt.deltaker.utils.DeltakerUtils
import no.nav.amt.deltaker.utils.buildManagedKafkaConsumer
import no.nav.amt.internapi.hendelse.HendelseType
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
 * og behandler godkjente hendelser av følgende typer:
 * - [TotrinnskontrollHendelsePayload.TotrinnskontrollType.ENKELTPLASS_OKONOMI] — godkjent søkt inn-deltakelse:
 *   fatter vedtak, oppdaterer prisinfo og setter ny deltakerstatus
 * - [TotrinnskontrollHendelsePayload.TotrinnskontrollType.ENKELTPLASS_PRISENDRING] — godkjent prisendring:
 *   godkjenner prisinfo uten å endre deltakerstatus
 *
 * Ikke-godkjente hendelser (f.eks. RETURNERT) oppdaterer kun prisinfoStatus og prosesseres ikke videre.
 *
 * I dev-miljø brukes et `skipFilter` for å hoppe over kjente ugyldige meldinger
 * på lave offsets uten å trigge retry.
 *
 * @param deltakerRepository repository for oppslag av enkeltplassdeltakere
 * @param deltakerService tjeneste for oppdatering og publisering av deltaker
 * @param vedtakService tjeneste for å fatte vedtak ved godkjent økonomi
 * @param distribuerEndringService tjeneste for å produsere hendelser etter godkjenning
 * @param navAnsattRepository repository for oppslag av Nav-ansatt som sist endret vedtaket
 * @param navEnhetRepository repository for oppslag av Nav-enhet som sist endret vedtaket
 */
class TotrinnskontrollConsumer(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerService: DeltakerService,
    private val vedtakService: VedtakService,
    private val distribuerEndringService: DistribuerEndringService,
    private val navAnsattRepository: NavAnsattRepository,
    private val navEnhetRepository: NavEnhetRepository,
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
     * og prosesserer kun godkjente ENKELTPLASS_OKONOMI- og ENKELTPLASS_PRISENDRING-hendelser.
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

        val totrinnskontrollHendelse = objectMapper.readValue<TotrinnskontrollHendelsePayload>(value)

        // herfra bryr oss kun om økonomi godkjent
        if (totrinnskontrollHendelse.status != TotrinnskontrollHendelsePayload.Status.GODKJENT) {
            PrisinfoRepository.oppdaterStatus(
                prisinformasjonId = totrinnskontrollHendelse.id,
                status = PrisinfoDbo.PrisinfoStatus.valueOf(totrinnskontrollHendelse.status.name),
            )

            log.info(
                "Totrinnskontroll ${totrinnskontrollHendelse.id} har status ${totrinnskontrollHendelse.status}, skipper videre prosessering.",
            )
            return
        }

        val deltaker = deltakerRepository
            .getEnkeltplassdeltaker(totrinnskontrollHendelse.entityId)
            .getOrThrow()

        val prisinfoStatus = PrisinfoRepository.hentPrisinfoStatus(
            gjennomforingId = deltaker.deltakerliste.id,
            prisinformasjonId = totrinnskontrollHendelse.id,
        ) ?: run {
            if (Environment.isDev()) {
                // dette kan skje i dev i en overgangsfase grunnet ny db-struktur
                log.warn("Fant ikke prisinfo for deltaker ${deltaker.id} med id ${totrinnskontrollHendelse.id}")
                return
            } else {
                error("Fant ikke prisinfo for deltaker ${deltaker.id} med id ${totrinnskontrollHendelse.id}")
            }
        }

        // Sjekk at prisinfo ikke allerede er godkjent
        // For å gjøre consumer idempotent.
        if (prisinfoStatus == PrisinfoDbo.PrisinfoStatus.GODKJENT) {
            log.info("Totrinnskontroll ${totrinnskontrollHendelse.id} er allerede godkjent, skipper videre prosessering.")
            return
        }

        when (totrinnskontrollHendelse.type) {
            TotrinnskontrollType.ENKELTPLASS_OKONOMI -> {
                processGodkjentInnsoking(
                    deltaker = deltaker,
                    prisinfoId = totrinnskontrollHendelse.id,
                )
            }

            TotrinnskontrollType.ENKELTPLASS_PRISENDRING ->
                processGodkjentPrisEndring(
                    deltaker = deltaker,
                    prisinfoId = totrinnskontrollHendelse.id,
                )

            else -> error("Uventet totrinnskontrolltype: ${totrinnskontrollHendelse.type}")
        }
    }

    /**
     * Prosesserer godkjent prisendring for en deltaker.
     *
     * Setter prisinfo til rolle GJELDENDE og status GODKJENT.
     *
     * @param deltaker Deltakeren hvis prisinfo skal godkjennes.
     * @param prisinfoId ID til prisinfoen som skal godkjennes.
     */
    internal fun processGodkjentPrisEndring(
        deltaker: Deltaker,
        prisinfoId: UUID,
    ) {
        // her skal det gjøres mer senere
        PrisinfoRepoAdapter.godkjennOkonomi(
            gjennomforingId = deltaker.deltakerliste.id,
            prisinformasjonId = prisinfoId,
        )
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
     * @param deltaker Deltakeren hvor økonomi skal godkjennes
     * @param prisinfoId ID til prisinfoen som skal godkjennes.
     */
    internal fun processGodkjentInnsoking(
        deltaker: Deltaker,
        prisinfoId: UUID,
    ) {
        log.info("Behandler godkjent totrinnskontroll for deltaker ${deltaker.id}")

        if (deltaker.status.type != DeltakerStatus.Type.SOKT_INN) {
            log.warn("Deltaker ${deltaker.id} har status ${deltaker.status.type} og kan ikke godkjennes med totrinnskontroll.")
            return
        }

        deltakerService.upsertAndProduceDeltaker(
            deltaker = deltaker,
            erDeltakerSluttdatoEndret = false,
            beforeUpsert = { deltaker ->

                // setter prisinformasjon til rolle = GJELDENDE og oppdaterer status til GODKJENT
                PrisinfoRepoAdapter.godkjennOkonomi(
                    gjennomforingId = deltaker.deltakerliste.id,
                    prisinformasjonId = prisinfoId,
                )

                vedtakService.godkjentOkonomiFattVedtak(deltaker = deltaker)
                deltaker.copy(
                    status = DeltakerUtils.nyDeltakerStatus(nyDeltakerStatus(deltaker)),
                )
            },
            afterUpsert = { deltaker ->
                val vedtak = deltaker.vedtaksinformasjon ?: error(
                    "Kan ikke produsere hendelse for økonomi godkjent for deltaker ${deltaker.id} uten vedtak",
                )

                distribuerEndringService.produceHendelseForUtkast(
                    deltaker = deltaker,
                    navAnsatt = navAnsattRepository.getOrThrow(vedtak.sistEndretAv),
                    enhet = navEnhetRepository.getOrThrow(vedtak.sistEndretAvEnhet),
                ) { utkastDto -> HendelseType.EnkeltplassOkonomiGodkjennUtkast(utkastDto) }
            },
        )

        log.info("Totrinnskontrollhendelse behandlet for deltaker ${deltaker.id}")
    }

    /**
     * Returnerer `true` når payload er en hendelse som skal behandles.
     *
     * Følgende typer behandles:
     * - [TotrinnskontrollType.ENKELTPLASS_OKONOMI] — søkt inn deltakelse godkjent
     * - [TotrinnskontrollType.ENKELTPLASS_PRISENDRING] — prisendring for deltakelse godkjent
     *
     * Metoden leser kun ut feltet `type` for å unngå deserialiseringsfeil på hendelser
     * med andre feltstrukturer som uansett skal ignoreres.
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

        /**
         * Bestemmer ny deltakerstatus basert på start- og sluttdato relativt til dagens dato.
         *
         * - Sluttdato er passert → [DeltakerStatus.Type.FULLFORT]
         * - Startdato er i fremtiden → [DeltakerStatus.Type.VENTER_PA_OPPSTART]
         * - Ellers → [DeltakerStatus.Type.DELTAR]
         *
         * @param deltaker Deltakeren som skal få ny status.
         * @throws IllegalStateException hvis start- eller sluttdato mangler.
         */
        internal fun nyDeltakerStatus(deltaker: Deltaker): DeltakerStatus.Type {
            val idag = LocalDate.now()
            val startdato = deltaker.startdato ?: error("Startdato mangler for deltaker ${deltaker.id}")
            val sluttdato = deltaker.sluttdato ?: error("Sluttdato mangler for deltaker ${deltaker.id}")

            return when {
                sluttdato.isBefore(idag) -> DeltakerStatus.Type.FULLFORT
                startdato.isAfter(idag) -> DeltakerStatus.Type.VENTER_PA_OPPSTART
                else -> DeltakerStatus.Type.DELTAR
            }
        }
    }
}
