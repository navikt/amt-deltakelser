package no.nav.amt.aktivitetskort.service

import no.nav.amt.aktivitetskort.client.AmtArrangorClient
import no.nav.amt.aktivitetskort.domain.AktivitetStatus
import no.nav.amt.aktivitetskort.domain.Aktivitetskort
import no.nav.amt.aktivitetskort.domain.Arrangor
import no.nav.amt.aktivitetskort.domain.Deltaker
import no.nav.amt.aktivitetskort.domain.DeltakerStatusModel
import no.nav.amt.aktivitetskort.kafka.consumer.dto.ArrangorDto
import no.nav.amt.aktivitetskort.kafka.consumer.toModel
import no.nav.amt.aktivitetskort.kafka.producer.AktivitetskortProducer
import no.nav.amt.aktivitetskort.repositories.ArrangorRepository
import no.nav.amt.aktivitetskort.repositories.DeltakerRepository
import no.nav.amt.aktivitetskort.repositories.DeltakerlisteRepository
import no.nav.amt.aktivitetskort.repositories.TiltakstypeRepository
import no.nav.amt.aktivitetskort.service.StatusMapping.deltakerStatusTilAktivitetStatus
import no.nav.amt.aktivitetskort.utils.RepositoryResult
import no.nav.amt.lib.models.deltaker.DeltakerKafkaPayload
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
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
    private val tiltakstypeRepository: TiltakstypeRepository,
    private val deltakerRepository: DeltakerRepository,
    private val aktivitetskortService: AktivitetskortService,
    private val amtArrangorClient: AmtArrangorClient,
    private val aktivitetskortProducer: AktivitetskortProducer,
    private val transactionTemplate: TransactionTemplate,
    private val unleashToggle: CommonUnleashToggle,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun deltakerHendelse(
        id: UUID,
        deltaker: DeltakerKafkaPayload?,
        offset: Long,
    ) {
        if (deltaker == null) return handterSlettetDeltaker(id)

        if (deltakerStatusTilAktivitetStatus(deltaker.status.type).isFailure) {
            log.info("Kan ikke lage aktivitetskort for deltaker ${deltaker.id} med status ${deltaker.status.type}")
            return
        }

        transactionTemplate.executeWithoutResult {
            when (val result = deltakerRepository.upsert(deltaker.toModel(), offset)) {
                is RepositoryResult.Modified -> {
                    log.info("Ny hendelse for deltaker ${deltaker.id}: Oppdatering")
                    val aktivitetskort = aktivitetskortService.lagAktivitetskort(result.data)
                    if (aktivitetskort == null) {
                        log.warn("aktivitetskort for deltaker ${deltaker.id} ble ikke oppdatert.")
                        return@executeWithoutResult
                    }
                    aktivitetskortProducer.send(aktivitetskort)
                }

                is RepositoryResult.Created -> {
                    log.info("Ny hendelse for deltaker ${deltaker.id}: Opprettelse")
                    val aktivitetskort = aktivitetskortService.lagAktivitetskort(result.data)
                    if (aktivitetskort == null) {
                        log.warn("aktivitetskort for deltaker ${deltaker.id} ble ikke opprettet")
                        return@executeWithoutResult
                    }
                    aktivitetskortProducer.send(aktivitetskort)
                }

                is RepositoryResult.NoChange -> {
                    log.info("Ny hendelse for deltaker ${deltaker.id}: Ingen endring")
                }
            }
            log.info("Konsumerte melding med deltaker $id, offset $offset")
        }
    }

    fun deltakerlisteHendelse(
        id: UUID,
        value: String?,
    ) {
        if (value == null) {
            deltakerlisteRepository.delete(id)
            return
        }

        val deltakerlistePayload: GjennomforingV2KafkaPayload = objectMapper.readValue(value)

        if (!unleashToggle.skalLeseGjennomforing(deltakerlistePayload.tiltakskode.name)) {
            return
        }

        val arrangor = arrangorRepository.get(deltakerlistePayload.arrangor.organisasjonsnummer)
            ?: hentOgLagreArrangorFraAmtArrangor(deltakerlistePayload.arrangor.organisasjonsnummer)

        val tiltakstype = tiltakstypeRepository.getByTiltakskode(deltakerlistePayload.tiltakskode.name)
            ?: throw NoSuchElementException("Fant ikke tiltakstype med tiltakskode ${deltakerlistePayload.tiltakskode}")

        val deltakerlisteModel = deltakerlistePayload.toModel(
            { gruppe -> gruppe.toModel(arrangor.id, tiltakstype.navn) },
            { enkeltplass -> enkeltplass.toModel(arrangor.id, tiltakstype.navn) },
        )

        transactionTemplate.executeWithoutResult {
            when (val result = deltakerlisteRepository.upsert(deltakerlisteModel)) {
                is RepositoryResult.Modified -> {
                    log.info("Ny hendelse for deltakerliste ${deltakerlistePayload.id}: Oppdatering")
                    aktivitetskortProducer.send(aktivitetskortService.oppdaterAktivitetskort(result.data))
                }

                is RepositoryResult.Created -> {
                    log.info("Ny hendelse for deltakerliste ${deltakerlistePayload.id}: Opprettelse")
                }

                is RepositoryResult.NoChange -> {
                    log.info("Ny hendelse for deltakerliste ${deltakerlistePayload.id}: Ingen endring")
                }
            }
            log.info("Konsumerte melding med deltakerliste ${deltakerlistePayload.id}")
        }
    }

    fun arrangorHendelse(
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
                    val aktivitetskort = aktivitetskortService.oppdaterAktivitetskort(result.data)
                    aktivitetskortProducer.send(aktivitetskort)
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

    private fun lagreArrangorMedOverordnetArrangor(arrangorMedOverordnetArrangor: AmtArrangorClient.ArrangorMedOverordnetArrangorDto) {
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

        aktivitetskortService
            .getSisteMeldingForDeltaker(deltaker.id)
            ?.also { avbrytAktivitetskort(it.aktivitetskort, deltaker) }

        log.info("Mottok tombstone for deltaker: $deltakerId og slettet deltaker")
        deltakerRepository.delete(deltakerId)
    }

    private fun avbrytAktivitetskort(
        aktivitetskort: Aktivitetskort,
        deltaker: Deltaker,
    ) {
        if (skalAvbryteAktivtetskort(aktivitetskort.aktivitetStatus)) {
            val avbruttDeltaker = deltaker.copy(status = DeltakerStatusModel(DeltakerStatus.Type.AVBRUTT, null))

            aktivitetskortProducer.send(aktivitetskortService.oppdaterAktivitetskortForSlettetdeltaker(avbruttDeltaker, aktivitetskort.id))
            log.info(
                "Mottok tombstone for deltaker: ${deltaker.id} som hadde status: ${deltaker.status.type}. " +
                    "Avbrøt deltakelse og aktivitetskort: ${aktivitetskort.id}.",
            )
        }
    }

    private fun skalAvbryteAktivtetskort(status: AktivitetStatus?): Boolean = when (status) {
        AktivitetStatus.FORSLAG,
        AktivitetStatus.PLANLAGT,
        AktivitetStatus.GJENNOMFORES,
        -> true

        else -> false
    }

    fun DeltakerKafkaPayload.toModel() = Deltaker(
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
