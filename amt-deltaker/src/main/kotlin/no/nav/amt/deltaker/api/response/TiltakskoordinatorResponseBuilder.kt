package no.nav.amt.deltaker.api.response

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.service.DeltakerHistorikkService
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.tiltaksarrangor.vurdering.VurderingRepository
import no.nav.amt.deltaker.veileder.DeltakerLaaseService
import no.nav.amt.internapi.deltaker.response.DeltakelsesmengderResponse
import no.nav.amt.internapi.deltaker.response.DeltakerResponse
import no.nav.amt.internapi.deltaker.response.DeltakereResponse
import no.nav.amt.internapi.deltaker.response.GjennomforingResponse
import no.nav.amt.internapi.deltaker.response.VurderingResponse
import no.nav.amt.lib.ktor.clients.distribusjon.AmtDistribusjonClient
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.Vurdering
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.utils.GenericCache
import java.time.LocalDate

/**
 * Bygger respons for tiltakskoordinator-endepunktet. Optimalisert for kall med mange deltakere
 * (kan være >2000 per request).
 *
 * Optimaliseringer sammenlignet med [DeltakerResponseBuilder] (som håndterer én deltaker om gangen):
 *   - `erLaastForEndringer` beregnes via [DeltakerLaaseService.erLaastForEndringerForDeltakere] i
 *     **én** spisset SQL-spørring for alle deltakere, i stedet for én spørring per deltaker
 *   - `soktInnDato` hentes via [DeltakerHistorikkService.getSoktInnDatoer] i **én** spisset SQL
 *     for alle deltakere, i stedet for opptil 3 sekvensielle DB-oppslag per deltaker
 *   - Forslag (VenterPaSvar) hentes via [ForslagRepository.getVenterPaSvarForDeltakere] i **én**
 *     SQL med JSONB-filter, i stedet for hent-alt + filter per deltaker
 *   - Siste vurdering hentes via [VurderingRepository.getSisteVurderingForDeltakere] i **én**
 *     SQL med window function, i stedet for hent-alt + maxBy per deltaker
 *   - `gjennomforing`-responsen bygges én gang (alle deltakere i kallet hører til samme
 *     gjennomføring) og gjenbrukes — sparer N-1 arrangør-DB-oppslag
 *   - Nav-ansatte og Nav-enheter hentes i ett bulk-oppslag på tvers av alle deltakere
 *     i stedet for ett oppslag per deltaker — sparer 2(N-1) DB-roundtrips
 *   - Alle 6 bulk-spørringer kjøres parallelt på `Dispatchers.IO` via `withContext` + `async`,
 *     begrenset til [MAX_PARALLEL_DB_QUERIES] samtidige spørringer via [Semaphore] for å unngå
 *     å mette HikariCP-poolen (10 connections totalt)
 *
 * Felter som utelates (ikke brukt i tiltakskoordinator-frontenden):
 *   - `deltakelsesmengder` — alltid null
 *   - `vedtaksinformasjon` — alltid null
 *   - `importertFraArena` — alltid null
 *   - `gjennomforing.kodeverkValg` — alltid tom
 *
 * `navBruker.erDigital` slås opp via [AmtDistribusjonClient.digitalBruker] per deltaker
 *
 * Felles mapping-logikk er trukket ut i [SharedResponseMappers].
 */
class TiltakskoordinatorResponseBuilder(
    private val arrangorService: ArrangorService,
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
    private val deltakerHistorikkService: DeltakerHistorikkService,
    private val forslagRepository: ForslagRepository,
    private val vurderingRepository: VurderingRepository,
    private val deltakerLaaseService: DeltakerLaaseService,
    private val amtDistribusjonClient: AmtDistribusjonClient,
) {
    suspend fun buildResponse(deltakere: List<Deltaker>): DeltakereResponse {
        if (deltakere.isEmpty()) return DeltakereResponse(emptyList())

        // alle deltakere hører til samme gjennomføring, så bygg gjennomføringsresponsen én gang og gjenbruk
        val gjennomforingResponse = SharedResponseMappers.buildGjennomforingResponse(
            deltakerliste = deltakere.first().deltakerliste,
            arrangorService = arrangorService,
            kodeverkValg = emptySet(),
        )

        val deltakerIder = deltakere.map { it.id }.toSet()

        // kjør uavhengige DB-spørringer parallelt på IO-dispatcher, men begrens samtidighet
        // for å unngå å mette connection-poolen (HikariCP er konfigurert med 10 connections).
        // Med MAX_PARALLEL_DB_QUERIES = 3 vil to samtidige store kall bruke maks 6 connections,
        // og lar 4 være ledige til andre endepunkter.
        val dbSemaphore = Semaphore(permits = MAX_PARALLEL_DB_QUERIES)
        return withContext(Dispatchers.IO) {
            val navAnsatteDeferred = async { dbSemaphore.withPermit { navAnsattService.hentNavAnsatteForDeltakere(deltakere) } }
            val navEnheterDeferred = async { dbSemaphore.withPermit { navEnhetService.hentNavEnheterForDeltakere(deltakere) } }
            val laaseStatusDeferred = async {
                dbSemaphore.withPermit { deltakerLaaseService.erLaastForEndringerForDeltakere(deltakere) }
            }
            val soktInnDatoerDeferred = async {
                dbSemaphore.withPermit { deltakerHistorikkService.getSoktInnDatoer(deltakerIder) }
            }
            val forslagDeferred = async { dbSemaphore.withPermit { forslagRepository.getVenterPaSvarForDeltakere(deltakerIder) } }
            val vurderingDeferred = async {
                dbSemaphore.withPermit { vurderingRepository.getSisteVurderingForDeltakere(deltakerIder) }
            }

            val navAnsatte = navAnsatteDeferred.await()
            val navEnheter = navEnheterDeferred.await()
            val laaseStatusPerDeltaker = laaseStatusDeferred.await()
            val soktInnDatoer = soktInnDatoerDeferred.await()
            val forslagPerDeltaker = forslagDeferred.await()
            val sisteVurderingPerDeltaker = vurderingDeferred.await()

            DeltakereResponse(
                deltakere.map {
                    buildDeltakerResponse(
                        deltaker = it,
                        gjennomforingResponse = gjennomforingResponse,
                        navAnsatte = navAnsatte,
                        navEnheter = navEnheter,
                        erLaastForEndringer = laaseStatusPerDeltaker[it.id] ?: false,
                        // Caffeine-cachen inne i AmtDistribusjonClient dedupliserer gjentatte oppslag
                        erDigital = amtDistribusjonClient.digitalBruker(it.navBruker.personident),
                        soktInnDato = soktInnDatoer[it.id],
                        endringsforslagFraArrangor = forslagPerDeltaker[it.id].orEmpty(),
                        sisteVurdering = sisteVurderingPerDeltaker[it.id],
                    )
                },
            )
        }
    }

    private fun buildDeltakerResponse(
        deltaker: Deltaker,
        gjennomforingResponse: GjennomforingResponse,
        navAnsatte: GenericCache<NavAnsatt>,
        navEnheter: GenericCache<NavEnhet>,
        erLaastForEndringer: Boolean,
        erDigital: Boolean,
        soktInnDato: LocalDate?,
        endringsforslagFraArrangor: List<Forslag>,
        sisteVurdering: Vurdering?,
    ): DeltakerResponse = DeltakerResponse(
        id = deltaker.id,
        navBruker = SharedResponseMappers.buildNavBrukerResponse(
            navBruker = deltaker.navBruker,
            navEnheter = navEnheter,
            navAnsatte = navAnsatte,
            erDigital = erDigital,
        ),
        gjennomforing = gjennomforingResponse,
        startdato = deltaker.startdato,
        sluttdato = deltaker.sluttdato,
        dagerPerUke = deltaker.dagerPerUke,
        deltakelsesprosent = deltaker.deltakelsesprosent,
        bakgrunnsinformasjon = deltaker.bakgrunnsinformasjon,
        deltakelsesinnhold = deltaker.deltakelsesinnhold,
        status = deltaker.status,
        vedtaksinformasjon = null,
        sistEndret = deltaker.sistEndret,
        kilde = deltaker.kilde,
        erManueltDeltMedArrangor = deltaker.erManueltDeltMedArrangor,
        opprettet = deltaker.opprettet,
        soktInnDato = soktInnDato,
        deltakelsesmengder = DeltakelsesmengderResponse(
            nesteDeltakelsesmengde = null,
            sisteDeltakelsesmengde = null,
        ),
        erLaastForEndringer = erLaastForEndringer,
        endringsforslagFraArrangor = endringsforslagFraArrangor,
        prisinformasjon = deltaker.deltakerliste.prisinformasjon,
        sisteVurdering = sisteVurdering?.let { VurderingResponse.fromVurdering(it) },
        importertFraArena = null,
    )

    companion object {
        /**
         * Maks antall parallelle DB-spørringer per request. HikariCP er konfigurert med 10 connections;
         * vi reserverer dermed maks 3 per request slik at 2 samtidige store kall bruker maks 6 av 10
         * connections og lar 4 være ledige til andre endepunkter.
         */
        private const val MAX_PARALLEL_DB_QUERIES = 3
    }
}
