package no.nav.amt.deltaker.bff

import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.utils.GenericCache

class AnsatteOgEnheterForDeltakerProvider(
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
) {
    suspend fun getAnsatteOgEnheterForDeltakerHistorikk(
        historikk: List<DeltakerHistorikk>,
    ): Pair<GenericCache<NavAnsatt>, GenericCache<NavEnhet>> {
        val ansatte = GenericCache(
            cacheName = "navAnsatte",
            itemMap = navAnsattService.hentAnsatteForHistorikk(historikk),
        )

        val enheter = GenericCache(
            cacheName = "navEnheter",
            itemMap = navEnhetService.hentEnheterForHistorikk(historikk),
        )

        return ansatte to enheter
    }

    fun getAnsatteOgEnheterForDeltaker(
        deltaker: Deltaker,
        forslagForDeltaker: List<Forslag>,
    ): Pair<GenericCache<NavAnsatt>, GenericCache<NavEnhet>> {
        // hent alle entries som behøver navn på Nav-ansatt eller -enhet
        val avvistAvNavAnsatte = forslagForDeltaker
            .map { it.status }
            .filterIsInstance<Forslag.Status.Avvist>()
            .map { it.avvistAv }

        val ansatte = GenericCache(
            cacheName = "navAnsatte",
            itemMap = navAnsattService.hentAnsatteForDeltaker(
                deltaker = deltaker,
                additionalIds = avvistAvNavAnsatte.map { it.id }.toSet(),
            ),
        )

        val enheter = GenericCache(
            cacheName = "navEnheter",
            itemMap = navEnhetService.hentNavEnheterForDeltaker(
                deltaker = deltaker,
                additionalIds = avvistAvNavAnsatte.map { it.enhetId }.toSet(),
            ),
        )

        return ansatte to enheter
    }
}
