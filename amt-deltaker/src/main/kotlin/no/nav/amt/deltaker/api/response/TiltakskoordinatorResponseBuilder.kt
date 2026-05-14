package no.nav.amt.deltaker.api.response

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
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.utils.GenericCache
import java.time.LocalDate

/**
 * Bygger respons for tiltakskoordinator-endepunktet. Optimalisert for kall med mange deltakere
 * (kan være >2000 per request).
 *
 * Forskjeller fra [DeltakerResponseBuilder]:
 *   - `navBruker.erDigital` slås opp via [AmtDistribusjonClient.digitalBruker] per deltaker
 *     (samme som [DeltakerResponseBuilder]). HTTP-kallene dedupliseres av Caffeine-cachen
 *     (15 min TTL) inne i klienten — repeterte visninger av samme liste er billige.
 *   - `erLaastForEndringer` beregnes via [DeltakerLaaseService.erLaastForEndringerForDeltakere] i
 *     **én** spisset SQL-spørring for alle deltakere, i stedet for 2 spørringer per deltaker
 *   - Ingen `deltakelsesmengder`-beregning (ikke i frontend-skjemaet)
 *   - Ingen `vedtaksinformasjon`- eller `importertFraArena`-mapping (ikke i frontend-skjemaet)
 *   - `soktInnDato` hentes via [DeltakerHistorikkService.getSoktInnDatoer] i **én** spisset SQL
 *     for alle deltakere, i stedet for opptil 3 sekvensielle DB-oppslag per deltaker
 *   - `gjennomforing.kodeverkValg` er alltid tom
 *   - `gjennomforing`-responsen bygges én gang (alle deltakere i kallet hører til samme
 *     gjennomføring) og gjenbrukes — sparer N-1 arrangør-DB-oppslag for store kall
 *   - Nav-ansatte og Nav-enheter hentes i ett bulk-oppslag på tvers av alle deltakere
 *     i stedet for ett oppslag per deltaker — sparer 2(N-1) DB-roundtrips for store kall
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

        // hent enheter og ansatte for alle deltakere
        val navAnsatte = navAnsattService.hentNavAnsatteForDeltakere(deltakere)
        val navEnheter = navEnhetService.hentNavEnheterForDeltakere(deltakere)

        // beregn låsing for alle deltakere i én spisset spørring
        val laaseStatusPerDeltaker = deltakerLaaseService.erLaastForEndringerForDeltakere(deltakere)

        // hent søkt-inn-dato for alle deltakere i én spisset spørring
        val soktInnDatoer = deltakerHistorikkService.getSoktInnDatoer(deltakere.map { it.id }.toSet())

        return DeltakereResponse(
            deltakere.map {
                buildDeltakerResponse(
                    deltaker = it,
                    gjennomforingResponse = gjennomforingResponse,
                    navAnsatte = navAnsatte,
                    navEnheter = navEnheter,
                    erLaastForEndringer = laaseStatusPerDeltaker[it.id] ?: false,
                    // Caffeine-cachen (15 min TTL) inne i AmtDistribusjonClient dedupliserer
                    // gjentatte oppslag på samme personident — repeterte visninger er billige.
                    erDigital = amtDistribusjonClient.digitalBruker(it.navBruker.personident),
                    soktInnDato = soktInnDatoer[it.id],
                )
            },
        )
    }

    private fun buildDeltakerResponse(
        deltaker: Deltaker,
        gjennomforingResponse: GjennomforingResponse,
        navAnsatte: GenericCache<NavAnsatt>,
        navEnheter: GenericCache<NavEnhet>,
        erLaastForEndringer: Boolean,
        erDigital: Boolean,
        soktInnDato: LocalDate?,
    ): DeltakerResponse {
        val endringsforslagForDeltaker =
            SharedResponseMappers.hentEndringsforslagVenterPaSvar(forslagRepository, deltaker.id)

        val sisteVurdering = SharedResponseMappers.hentSisteVurdering(vurderingRepository, deltaker.id)

        return DeltakerResponse(
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
            endringsforslagFraArrangor = endringsforslagForDeltaker,
            prisinformasjon = deltaker.deltakerliste.prisinformasjon,
            sisteVurdering = sisteVurdering?.let { VurderingResponse.fromVurdering(it) },
            importertFraArena = null,
        )
    }
}
