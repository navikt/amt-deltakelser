package no.nav.amt.deltaker.deltaker.forslag

import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.forslag.kafka.ArrangorMeldingProducer
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.lib.models.arrangor.melding.Forslag
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class ForslagService(
    private val forslagRepository: ForslagRepository,
    private val arrangorMeldingProducer: ArrangorMeldingProducer,
    private val deltakerRepository: DeltakerRepository,
    private val deltakerProducerService: DeltakerProducerService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun upsertAndProduce(forslag: Forslag) {
        forslagRepository.upsert(forslag)

        when (forslag.status) {
            is Forslag.Status.Godkjent,
            Forslag.Status.VenterPaSvar,
            -> {
            }

            is Forslag.Status.Avvist,
            is Forslag.Status.Erstattet,
            is Forslag.Status.Tilbakekalt,
            -> {
                val deltaker = deltakerRepository.get(forslag.deltakerId).getOrThrow()
                deltakerProducerService.produce(deltaker, publiserTilDeltakerV1 = false)
            }
        }
        log.info("Lagret forslag ${forslag.id}")
    }

    fun godkjennForslag(
        forslagId: UUID,
        godkjentAvAnsattId: UUID,
        godkjentAvEnhetId: UUID,
    ): Forslag {
        val opprinneligForslag = forslagRepository.get(forslagId).getOrThrow()
        val godkjentForslag = opprinneligForslag.copy(
            status = Forslag.Status.Godkjent(
                godkjentAv = Forslag.NavAnsatt(
                    id = godkjentAvAnsattId,
                    enhetId = godkjentAvEnhetId,
                ),
                godkjent = LocalDateTime.now(),
            ),
        )
        upsertAndProduce(godkjentForslag)
        arrangorMeldingProducer.produce(godkjentForslag)

        log.info("Godkjent forslag for deltaker ${opprinneligForslag.deltakerId}")
        return godkjentForslag
    }
}
