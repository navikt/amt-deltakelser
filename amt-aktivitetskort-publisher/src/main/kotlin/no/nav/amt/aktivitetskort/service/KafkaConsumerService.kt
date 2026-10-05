package no.nav.amt.aktivitetskort.service

import no.nav.amt.aktivitetskort.client.AmtArrangorClient
import no.nav.amt.aktivitetskort.client.AmtDeltakerClient
import no.nav.amt.aktivitetskort.client.response.ArrangorMedOverordnetArrangorResponse
import no.nav.amt.aktivitetskort.domain.AktivitetStatus
import no.nav.amt.aktivitetskort.domain.Arrangor
import no.nav.amt.aktivitetskort.domain.Deltaker
import no.nav.amt.aktivitetskort.domain.DeltakerDbo
import no.nav.amt.aktivitetskort.domain.DeltakerStatusModel
import no.nav.amt.aktivitetskort.kafka.consumer.dto.ArrangorDto
import no.nav.amt.aktivitetskort.kafka.consumer.toDeltakerliste
import no.nav.amt.aktivitetskort.repositories.ArrangorRepository
import no.nav.amt.aktivitetskort.repositories.DeltakerRepository
import no.nav.amt.aktivitetskort.repositories.DeltakerlisteRepository
import no.nav.amt.aktivitetskort.service.StatusMapping.deltakerStatusTilAktivitetStatus
import no.nav.amt.aktivitetskort.utils.RepositoryResult
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.kafka.AmtGjennomforingPayload
import no.nav.amt.lib.models.kafka.DeltakerKafkaPayload
import no.nav.amt.lib.models.kafka.GjennomforingV2KafkaPayload.Companion.deltakerlisteTombstoneBlacklist
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.util.UUID

@Service
class KafkaConsumerService(
    private val arrangorRepository: ArrangorRepository,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val deltakerRepository: DeltakerRepository,
    private val aktivitetskortService: AktivitetskortService,
    private val amtArrangorClient: AmtArrangorClient,
    private val transactionTemplate: TransactionTemplate,
    private val objectMapper: ObjectMapper,
    private val amtDeltakerClient: AmtDeltakerClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handleDeltaker(
        id: UUID,
        deltakerPayload: DeltakerKafkaPayload?,
        offset: Long,
    ) {
        if (deltakerPayload == null) return handterSlettetDeltaker(id)

        if (deltakerStatusTilAktivitetStatus(deltakerPayload.status.type).isFailure) {
            log.info("Kan ikke lage aktivitetskort for deltaker ${deltakerPayload.id} med status ${deltakerPayload.status.type}")
            return
        }
        val deltaker = amtDeltakerClient
            .getDeltaker(deltakerPayload.id)
            .let { Deltaker.fromDeltakerResponse(it) }

        transactionTemplate.executeWithoutResult {
            val deltakerUpsertResult = deltakerRepository.upsert(
                deltaker = deltakerPayload.toDbo(),
                offset = offset,
                buyPassEqualityCheck = deltaker.gjennomforing.tiltakstype.erOpplaeringstiltak && deltaker.kilde == Kilde.KOMET,
            )
            when (deltakerUpsertResult) {
                is RepositoryResult.Modified -> {
                    log.info("Ny hendelse for deltaker ${deltakerPayload.id}: Oppdatering")
                    val aktivitetskort = aktivitetskortService.lagAktivitetskort(deltaker)
                    if (aktivitetskort == null) {
                        log.warn("aktivitetskort for deltaker ${deltakerPayload.id} ble ikke oppdatert.")
                        return@executeWithoutResult
                    }
                }

                is RepositoryResult.Created -> {
                    log.info("Ny hendelse for deltaker ${deltaker.id}: Opprettelse")
                    val aktivitetskort = aktivitetskortService.lagAktivitetskort(deltaker)
                    if (aktivitetskort == null) {
                        log.warn("aktivitetskort for deltaker ${deltaker.id} ble ikke opprettet")
                        return@executeWithoutResult
                    }
                }

                is RepositoryResult.NoChange -> {
                    log.info("Ny hendelse for deltaker ${deltaker.id}: Ingen endring")
                }
            }
            log.info("Konsumerte melding med deltaker $id, offset $offset")
        }
    }

    fun handleGjennomforing(
        id: UUID,
        value: String?,
    ) {
        if (value == null) {
            log.info("Mottok tombstone for gjennomføring: $id")
            val antallDeltakere = deltakerRepository.getAntallDeltakereForDeltakerliste(id)

            if (id !in deltakerlisteTombstoneBlacklist && antallDeltakere == 0) {
                deltakerlisteRepository.delete(id)
            } else {
                log.error(
                    "Ignorerer tombstone for $id. " +
                        "Deltakerliste er svartelistet eller har deltakere. Antall deltakere: $antallDeltakere",
                )
            }

            return
        }

        val payload: AmtGjennomforingPayload = objectMapper.readValue(value)

        val arrangor = arrangorRepository.get(payload.arrangor.organisasjonsnummer)
            ?: hentOgLagreArrangorFraAmtArrangor(payload.arrangor.organisasjonsnummer)

        val deltakerlisteModel = payload.toDeltakerliste(arrangor.id)

        transactionTemplate.executeWithoutResult {
            when (val result = deltakerlisteRepository.upsert(deltakerlisteModel)) {
                is RepositoryResult.Modified -> {
                    log.info("Ny hendelse for deltakerliste ${payload.id}: Oppdatering")
                    aktivitetskortService.oppdaterAktivitetskort(result.data.id)
                }

                is RepositoryResult.Created -> {
                    log.info("Ny hendelse for deltakerliste ${payload.id}: Opprettelse")
                }

                is RepositoryResult.NoChange -> {
                    log.info("Ny hendelse for deltakerliste ${payload.id}: Ingen endring")
                }
            }
            log.info("Konsumerte melding med deltakerliste ${payload.id}")
        }
    }

    // Hvis vi skal sløyfe denne consumeren så må amt-deltaker også dele arrangør navnet
    // eller sende oppdatering på gjennomforing-intern når arrangør navn endres
    fun handleArrangor(
        id: UUID,
        arrangor: ArrangorDto?,
    ) {
        if (arrangor == null) return
        if (arrangor.overordnetArrangorId != null && arrangorRepository.get(arrangor.overordnetArrangorId) == null) {
            hentOgLagreArrangorFraAmtArrangor(arrangor.overordnetArrangorId)
        }

        transactionTemplate.executeWithoutResult {
            when (val result = arrangorRepository.upsert(arrangor.toModel())) {
                is RepositoryResult.Modified -> {
                    log.info("Ny hendelse for arrangor ${arrangor.id}: Oppdatering")
                    aktivitetskortService.oppdaterAktivitetskort(result.data)
                }

                is RepositoryResult.Created -> {
                    log.info("Ny hendelse for arrangør ${arrangor.id}: Opprettelse")
                }

                is RepositoryResult.NoChange -> {
                    log.info("Ny hendelse for arrangør ${arrangor.id}: Ingen endring")
                }
            }
            log.info("Konsumerte melding med arrangør $id")
        }
    }

    private fun hentOgLagreArrangorFraAmtArrangor(virksomhetsnummer: String): Arrangor {
        val arrangorMedOverordnetArrangor = amtArrangorClient.hentArrangor(virksomhetsnummer)
        lagreArrangorMedOverordnetArrangor(arrangorMedOverordnetArrangor)
        return arrangorRepository.get(virksomhetsnummer)
            ?: throw RuntimeException("Fant ikke arrangør med id ${arrangorMedOverordnetArrangor.id} som vi nettopp lagret")
    }

    private fun hentOgLagreArrangorFraAmtArrangor(arrangorId: UUID) {
        val arrangorMedOverordnetArrangor = amtArrangorClient.hentArrangor(arrangorId)
        lagreArrangorMedOverordnetArrangor(arrangorMedOverordnetArrangor)
        log.info("Hentet og lagret overordnet arrangør med id $arrangorId som manglet i databasen")
    }

    private fun lagreArrangorMedOverordnetArrangor(arrangorMedOverordnetArrangor: ArrangorMedOverordnetArrangorResponse) {
        arrangorMedOverordnetArrangor.overordnetArrangor?.let {
            arrangorRepository.upsert(
                Arrangor(
                    id = it.id,
                    organisasjonsnummer = it.organisasjonsnummer,
                    navn = it.navn,
                    overordnetArrangorId = it.overordnetArrangorId,
                ),
            )
        }
        arrangorRepository.upsert(
            Arrangor(
                id = arrangorMedOverordnetArrangor.id,
                organisasjonsnummer = arrangorMedOverordnetArrangor.organisasjonsnummer,
                navn = arrangorMedOverordnetArrangor.navn,
                overordnetArrangorId = arrangorMedOverordnetArrangor.overordnetArrangor?.id,
            ),
        )
    }

    private fun handterSlettetDeltaker(deltakerId: UUID) {
        val deltaker = deltakerRepository.get(deltakerId) ?: return
        val melding = aktivitetskortService.getSisteMeldingForDeltaker(deltaker.id)

        if (melding != null && skalAvbryteAktivitetskort(melding.aktivitetskort.aktivitetStatus)) {
            aktivitetskortService.oppdaterAktivitetskortForSlettetDeltaker(
                deltaker = deltaker,
                melding = melding,
            )
            log.info(
                "Mottok tombstone for deltaker: ${deltaker.id} som hadde status: ${deltaker.status.type}. " +
                    "Avbrøt deltakelse og aktivitetskort: ${melding.id}.",
            )
        } else {
            deltakerRepository.delete(deltakerId)
        }

        log.info("Mottok tombstone for deltaker: $deltakerId og slettet deltaker")
    }

    private fun skalAvbryteAktivitetskort(status: AktivitetStatus?): Boolean = when (status) {
        AktivitetStatus.FORSLAG,
        AktivitetStatus.PLANLAGT,
        AktivitetStatus.GJENNOMFORES,
        -> true

        else -> false
    }

    fun DeltakerKafkaPayload.toDbo() = DeltakerDbo(
        id = id,
        personident = personalia.personident,
        deltakerlisteId = deltakerliste.id,
        status = DeltakerStatusModel(status.type, status.aarsak, status.gyldigFra),
        dagerPerUke = dagerPerUke,
        prosentStilling = prosentStilling,
        oppstartsdato = oppstartsdato,
        sluttdato = sluttdato,
        deltarPaKurs = deltarPaKurs,
        kilde = kilde,
    )
}
