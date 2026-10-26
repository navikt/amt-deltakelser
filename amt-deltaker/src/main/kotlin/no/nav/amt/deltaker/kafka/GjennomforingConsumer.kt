package no.nav.amt.deltaker.kafka

import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.model.Gjennomforing
import no.nav.amt.deltaker.model.toModel
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.GjennomforingRepository
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.tiltak.TiltakRepository
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.utils.buildManagedKafkaConsumer
import no.nav.amt.deltaker.veileder.KladdService
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.kafka.GjennomforingV2KafkaPayload
import no.nav.amt.lib.models.kafka.GjennomforingV2KafkaPayload.Companion.deltakerlisteTombstoneBlacklist
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tools.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import java.util.UUID

class GjennomforingConsumer(
    private val gjennomforingRepository: GjennomforingRepository,
    private val deltakerRepository: DeltakerRepository,
    private val tiltakRepository: TiltakRepository,
    private val arrangorService: ArrangorService,
    private val deltakerService: DeltakerService,
    private val deltakerProducerService: DeltakerProducerService,
    private val kladdService: KladdService,
    private val amtGjennomforingProducer: AmtGjennomforingProducer,
    private val unleashToggle: CommonUnleashToggle,
) : Consumer<UUID, String?> {
    private val consumer = buildManagedKafkaConsumer(
        topic = Environment.GJENNOMFORING_V2_TOPIC,
        consumeFunc = ::consume,
    )
    val log: Logger = LoggerFactory.getLogger(javaClass)

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()

    suspend fun consume(
        key: UUID,
        value: String?,
    ) {
        if (value == null) {
            log.info("Mottok tombstone for gjennomføring: $key")
            val antallDeltakere = deltakerRepository.getAntallDeltakereForDeltakerliste(key)
            if (key !in deltakerlisteTombstoneBlacklist && antallDeltakere == 0) {
                Database.transaction {
                    gjennomforingRepository.delete(key)
                    amtGjennomforingProducer.produceTombstone(key)
                }
            } else {
                log.error(
                    "Ignorerer tombstone for $key. " +
                        "Deltakerliste er svartelistet eller har deltakere. Antall deltakere: $antallDeltakere",
                )
            }
        } else {
            handterGjennomforing(objectMapper.readValue(value))
        }
    }

    private suspend fun handterGjennomforing(gjennomforingPayload: GjennomforingV2KafkaPayload) {
        if (!unleashToggle.skalLeseGjennomforing(gjennomforingPayload.tiltakskode.name)) {
            return
        }

        // enkelte gjennomforinger skal ikke bli lest grunnet feil
        if (GjennomforingV2KafkaPayload.gjennomforingBlacklist.contains(gjennomforingPayload.id)) {
            return
        }

        gjennomforingPayload.assertPameldingstypeIsValid()

        val arrangor = arrangorService.hentArrangor(gjennomforingPayload.arrangor.organisasjonsnummer)
        val tiltakstype = tiltakRepository.get(gjennomforingPayload.tiltakskode)
            ?: throw NoSuchElementException("Fant ikke tiltakstype ${gjennomforingPayload.tiltakskode.name}")

        val gjennomforing = gjennomforingPayload.toModel(
            { gruppe -> gruppe.toModel(arrangor, tiltakstype) },
            { enkeltplass -> enkeltplass.toModel(arrangor, tiltakstype) },
        )

        val eksisterendeGjennomforing = gjennomforingRepository.get(gjennomforingPayload.id)

        if (gjennomforing.status == GjennomforingStatusType.AVLYST || gjennomforing.status == GjennomforingStatusType.AVBRUTT) {
            val kladderSomSkalSlettes = deltakerRepository.getKladderForDeltakerliste(gjennomforingPayload.id)
            kladderSomSkalSlettes.forEach { deltaker -> kladdService.slettKladd(deltaker.id) }

            log.info("Slettet ${kladderSomSkalSlettes.size} for deltakerliste ${gjennomforing.id} med status ${gjennomforing.status.name}")
        }

        if (eksisterendeGjennomforing != null) {
            if (eksisterendeGjennomforing == gjennomforing) {
                log.info("Deltakerliste med id ${gjennomforing.id} er uendret.")
                return
            }

            // deltakerliste med deltakere kan ikke endre pameldingstype eller oppstartstype
            gjennomforingPayload.assertValidChanges(
                antallDeltakere = deltakerRepository.getAntallDeltakereForDeltakerliste(eksisterendeGjennomforing.id),
                eksisterendePameldingstype = eksisterendeGjennomforing.pameldingstype,
                eksisterendeOppstartstype = eksisterendeGjennomforing.oppstart,
            )

            Database.transaction {
                gjennomforingRepository.upsert(gjennomforing)
                amtGjennomforingProducer.produce(gjennomforing.toAmtGjennomforingPayload())

                // Fiks for Arena-data hvor deltakerliste er avsluttet mens deltaker er aktiv.
                // Da skal deltakelsen fortsette å være aktiv
                if (!tiltakstype.tiltakskode.erArenaEnkeltplass()) {
                    handterDeltakere(
                        gjennomforingFromPayload = gjennomforing,
                        eksisterendeGjennomforing = eksisterendeGjennomforing,
                    )
                }

                publiserEnkeltplassDeltaker(eksisterendeGjennomforing)
            }
        } else {
            Database.transaction {
                gjennomforingRepository.upsert(gjennomforing)
                amtGjennomforingProducer.produce(gjennomforing.toAmtGjennomforingPayload())
            }
        }
    }

    private fun handterDeltakere(
        gjennomforingFromPayload: Gjennomforing,
        eksisterendeGjennomforing: Gjennomforing,
    ) {
        if (gjennomforingFromPayload.erAvlystEllerAvbrutt() && eksisterendeGjennomforing.status != gjennomforingFromPayload.status) {
            avsluttDeltakelserPaaDeltakerliste(gjennomforingFromPayload)
        }

        if (gjennomforingFromPayload.sluttDato != null &&
            eksisterendeGjennomforing.sluttDato != null &&
            gjennomforingFromPayload.sluttDato < eksisterendeGjennomforing.sluttDato
        ) {
            avgrensSluttdatoerTil(gjennomforingFromPayload)
        }
    }

    /**
     * Publiserer enkeltplassdeltaker til deltaker-topic når gjennomføringen fortsatt er i kladd-status.
     *
     * Dette brukes i flyten der Nav-veileder har opprettet deltaker før gjennomføringen er ferdig
     * opprettet i Mulighetsrommet. Når vi senere mottar oppdatering på gjennomføringen, publiseres
     * deltakeren slik at nedstrøms konsumenter får oppdatert data.
     *
     * Metoden gjør ingenting dersom:
     * - gjennomføringen ikke er av typen [GjennomforingType.Enkeltplass], eller
     * - gjennomføringen ikke har status [GjennomforingStatusType.KLADD].
     */
    internal fun publiserEnkeltplassDeltaker(gjennomforing: Gjennomforing) {
        if (!(
                gjennomforing.gjennomforingstype == GjennomforingType.Enkeltplass &&
                    gjennomforing.status == GjennomforingStatusType.KLADD
            )
        ) {
            return
        }

        val deltaker = deltakerRepository.getEnkeltplassdeltakerOrThrow(gjennomforing.id)
        deltakerProducerService.produce(deltaker)
    }

    internal fun avsluttDeltakelserPaaDeltakerliste(gjennomforing: Gjennomforing) {
        val deltakerePaAvbruttDeltakerliste = deltakerRepository
            .getDeltakereForAvsluttetDeltakerliste(gjennomforing.id)
            .map { it.copy(gjennomforing = gjennomforing) }

        deltakerService.avsluttDeltakere(deltakerePaAvbruttDeltakerliste)
    }

    internal fun avgrensSluttdatoerTil(gjennomforing: Gjennomforing) {
        deltakerRepository
            .getDeltakerHvorSluttdatoSkalEndres(gjennomforing.id)
            .forEach { deltaker ->
                deltakerRepository.upsert(
                    deltaker.copy(
                        sluttdato = gjennomforing.sluttDato,
                        sistEndret = LocalDateTime.now(),
                    ),
                )
                deltakerService.lagreDeltakerStatus(
                    deltakerId = deltaker.id,
                    nyDeltakerStatus = deltaker.status,
                    erDeltakerSluttdatoEndret = true,
                )
                deltakerProducerService.produce(
                    deltaker = deltakerService.getOrThrow(deltaker.id),
                    forcedUpdate = true,
                )

                log.info("Deltaker ${deltaker.id} fikk ny sluttdato")
            }
    }
}
