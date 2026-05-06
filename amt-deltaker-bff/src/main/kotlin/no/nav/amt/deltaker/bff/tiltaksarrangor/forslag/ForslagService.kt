package no.nav.amt.deltaker.bff.deltaker.forslag

import no.nav.amt.deltaker.bff.deltaker.forslag.kafka.ArrangorMeldingProducer
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class ForslagService(
    private val forslagRepository: ForslagRepository,
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
    private val arrangorMeldingProducer: ArrangorMeldingProducer,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun avvisForslag(
        opprinneligForslag: Forslag,
        begrunnelse: String,
        avvistAvAnsatt: String,
        avvistAvEnhet: String,
    ) {
        val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(avvistAvAnsatt)
        val navEnhet = navEnhetService.hentOpprettEllerOppdaterNavEnhet(avvistAvEnhet)

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
            forslagRepository.delete(opprinneligForslag.id)
            arrangorMeldingProducer.produce(avvistForslag)
        }

        log.info("Avvist forslag for deltaker ${opprinneligForslag.deltakerId}")
    }
}
