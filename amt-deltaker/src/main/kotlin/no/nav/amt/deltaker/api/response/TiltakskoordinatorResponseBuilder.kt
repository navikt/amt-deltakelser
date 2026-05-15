package no.nav.amt.deltaker.api.response

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import no.nav.amt.deltaker.api.response.TiltakskoordinatorResponseBuilder.Companion.MAX_PARALLEL_DB_QUERIES
import no.nav.amt.deltaker.digitalbruker.DigitalBrukerService
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.service.DeltakerHistorikkService
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.tiltaksarrangor.vurdering.VurderingRepository
import no.nav.amt.deltaker.veileder.DeltakerLaaseService
import no.nav.amt.internapi.deltaker.response.DeltakerResponse
import no.nav.amt.internapi.deltaker.response.DeltakereResponse
import no.nav.amt.internapi.deltaker.response.GjennomforingResponse
import no.nav.amt.internapi.deltaker.response.VurderingResponse
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
 *     begrenset av en **delt** [Semaphore] på prosessnivå slik at totalt antall samtidige
 *     DB-spørringer på tvers av alle requests aldri overstiger [MAX_PARALLEL_DB_QUERIES] av
 *     HikariCPs 10 connections — sikrer at andre endepunkter ikke sultes på connections
 *
 * Felter som utelates (ikke brukt i tiltakskoordinator-frontenden):
 *   - `deltakelsesmengder` — alltid null
 *   - `vedtaksinformasjon` — alltid null
 *   - `importertFraArena` — alltid null
 *   - `gjennomforing.kodeverkValg` — alltid tom
 *
 * `navBruker.erDigital` hentes via [DigitalBrukerService] som bruker en DB-backet cache med
 * 24-timers TTL — kun utdaterte/manglende entries hentes fra `amt-distribusjon`.
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
    private val digitalBrukerService: DigitalBrukerService,
) {
    suspend fun buildResponse(deltakere: List<Deltaker>): DeltakereResponse {
        if (deltakere.isEmpty()) return DeltakereResponse(emptyList())

        // alle deltakere hører til samme gjennomføring, så bygg gjennomføringsresponsen én gang og gjenbruk
        val gjennomforingResponse = SharedResponseMappers.buildGjennomforingResponse(
            deltakerliste = deltakere.first().deltakerliste,
            arrangorService = arrangorService,
            kodeverkValg = emptySet(),
            sertifiseringValg = emptySet(),
        )

        val deltakerIder = deltakere.map { it.id }.toSet()
        val personidenter = deltakere.map { it.navBruker.personident }.toSet()

        // kjør uavhengige DB-spørringer parallelt på IO-dispatcher, men begrens samtidighet
        // via en delt semaphore (se [DB_SEMAPHORE]) slik at totalt antall samtidige DB-spørringer
        // på tvers av alle requests aldri overstiger [MAX_PARALLEL_DB_QUERIES].
        return withContext(Dispatchers.IO) {
            val navAnsatteDeferred = async { DB_SEMAPHORE.withPermit { navAnsattService.hentNavAnsatteForDeltakere(deltakere) } }
            val navEnheterDeferred = async { DB_SEMAPHORE.withPermit { navEnhetService.hentNavEnheterForDeltakere(deltakere) } }
            val laaseStatusDeferred = async {
                DB_SEMAPHORE.withPermit { deltakerLaaseService.erLaastForEndringerForDeltakere(deltakere) }
            }
            val soktInnDatoerDeferred = async {
                DB_SEMAPHORE.withPermit { deltakerHistorikkService.getSoktInnDatoer(deltakerIder) }
            }
            val forslagDeferred = async { DB_SEMAPHORE.withPermit { forslagRepository.getVenterPaSvarForDeltakere(deltakerIder) } }
            val vurderingDeferred = async {
                DB_SEMAPHORE.withPermit { vurderingRepository.getSisteVurderingForDeltakere(deltakerIder) }
            }
            // `hentErDigitalForPersonidenter` gjør 1 SELECT + N HTTP-kall mot amt-distribusjon + 1 UPSERT.
            // Vi holder ikke DB_SEMAPHORE her — det ville blokkert poolen mens vi venter på HTTP.
            // De to DB-operasjonene er små nok til at de kan kjøre uten reservasjon.
            val erDigitalDeferred = async {
                digitalBrukerService.hentErDigitalForPersonidenter(personidenter)
            }

            val navAnsatte = navAnsatteDeferred.await()
            val navEnheter = navEnheterDeferred.await()
            val laaseStatusPerDeltaker = laaseStatusDeferred.await()
            val soktInnDatoer = soktInnDatoerDeferred.await()
            val forslagPerDeltaker = forslagDeferred.await()
            val sisteVurderingPerDeltaker = vurderingDeferred.await()
            val erDigitalPerPersonident = erDigitalDeferred.await()

            DeltakereResponse(
                deltakere.map {
                    buildDeltakerResponse(
                        deltaker = it,
                        gjennomforingResponse = gjennomforingResponse,
                        navAnsatte = navAnsatte,
                        navEnheter = navEnheter,
                        erLaastForEndringer = laaseStatusPerDeltaker[it.id] ?: false,
                        erDigital = erDigitalPerPersonident[it.navBruker.personident] ?: false,
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
        deltakelsesmengder = null,
        erLaastForEndringer = erLaastForEndringer,
        endringsforslagFraArrangor = endringsforslagFraArrangor,
        prisinformasjon = deltaker.deltakerliste.prisinformasjon,
        sisteVurdering = sisteVurdering?.let { VurderingResponse.fromVurdering(it) },
        importertFraArena = null,
    )

    companion object {
        /**
         * Maks antall parallelle DB-spørringer **på tvers av alle samtidige requests** i prosessen.
         * HikariCP er konfigurert med 10 connections totalt; ved å reservere 6 til denne builderen
         * sikrer vi at minst 4 connections alltid er ledige til andre endepunkter, uavhengig av
         * hvor mange tiltakskoordinator-kall som kjører samtidig.
         */
        private const val MAX_PARALLEL_DB_QUERIES = 6

        /**
         * Delt semaphore på tvers av alle samtidige `buildResponse`-kall.
         * Må være `companion object`-nivå for å fungere som en prosess-vid begrensning —
         * en per-request semaphore beskytter ikke poolen mot mange samtidige requests.
         */
        private val DB_SEMAPHORE = Semaphore(permits = MAX_PARALLEL_DB_QUERIES)
    }
}
