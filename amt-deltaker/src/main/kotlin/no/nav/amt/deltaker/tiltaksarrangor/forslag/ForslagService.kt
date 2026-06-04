package no.nav.amt.deltaker.tiltaksarrangor.forslag

import no.nav.amt.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorMeldingProducer
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class ForslagService(
    private val forslagRepository: ForslagRepository,
    private val arrangorMeldingProducer: ArrangorMeldingProducer,
    private val deltakerRepository: DeltakerRepository,
    private val deltakerProducerService: DeltakerProducerService,
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun upsertAndProduce(forslag: Forslag) {
        forslagRepository.upsert(forslag)

        when (forslag.status) {
            is Forslag.Status.Godkjent,
            Forslag.Status.VenterPaSvar,
            -> Unit

            is Forslag.Status.Avvist,
            is Forslag.Status.Erstattet,
            is Forslag.Status.Tilbakekalt,
            -> {
                val deltaker = deltakerRepository.get(forslag.deltakerId).getOrThrow()
                deltakerProducerService.produce(deltaker, publiserTilDeltakerV1 = false, publiserTilDeltakerEksternV1 = false)
            }
        }
        log.info("Lagret forslag ${forslag.id}")
    }

    suspend fun avvisForslag(
        forslagId: UUID,
        begrunnelse: String,
        avvistAvAnsattIdent: String,
        avvistAvEnhet: String,
    ) {
        val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(avvistAvAnsattIdent)
        val navEnhet = navEnhetService.hentEllerOpprettNavEnhet(avvistAvEnhet)
        val opprinneligForslag = forslagRepository.get(forslagId).getOrThrow()

        val avvistForslag = opprinneligForslag.copy(
            status = Forslag.Status.Avvist(
                avvistAv = Forslag.NavAnsatt(
                    id = navAnsatt.id,
                    enhetId = navEnhet.id,
                ),
                avvist = LocalDateTime.now(),
                begrunnelseFraNav = begrunnelse,
            ),
        )
        Database.transaction {
            upsertAndProduce(avvistForslag)
            arrangorMeldingProducer.produce(avvistForslag)
        }

        log.info("Avvist forslag for deltaker ${opprinneligForslag.deltakerId}")
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
