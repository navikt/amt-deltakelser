package no.nav.amt.deltaker.api.response

import no.nav.amt.deltaker.digitalbruker.DigitalBrukerService
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.model.Gjennomforing
import no.nav.amt.deltaker.model.Vedtaksinformasjon
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.service.DeltakerHistorikkService
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.tiltaksarrangor.vurdering.VurderingRepository
import no.nav.amt.deltaker.veileder.DeltakerLaaseService
import no.nav.amt.internapi.deltaker.response.DeltakelsesmengdeResponse
import no.nav.amt.internapi.deltaker.response.DeltakelsesmengderResponse
import no.nav.amt.internapi.deltaker.response.DeltakerResponse
import no.nav.amt.internapi.deltaker.response.GjennomforingResponse
import no.nav.amt.internapi.deltaker.response.NavBrukerResponse
import no.nav.amt.internapi.deltaker.response.VedtaksinformasjonResponse
import no.nav.amt.internapi.deltaker.response.VurderingResponse
import no.nav.amt.internapi.deltaker.response.nesteTilVisning
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengder
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.utils.GenericCache

class DeltakerResponseBuilder(
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
    private val digitalBrukerService: DigitalBrukerService,
    private val deltakerHistorikkService: DeltakerHistorikkService,
    private val forslagRepository: ForslagRepository,
    private val deltakerLaaseService: DeltakerLaaseService,
    private val vurderingRepository: VurderingRepository,
    private val deltakerRepository: DeltakerRepository,
) {
    suspend fun buildDeltakerResponse(
        deltaker: Deltaker,
        includePrisinfo: Boolean = true,
    ): DeltakerResponse {
        val endringsforslagForDeltaker = SharedResponseMappers.hentEndringsforslagVenterPaSvar(
            forslagRepository = forslagRepository,
            deltakerId = deltaker.id,
        )

        val navAnsatte = navAnsattService.hentNavAnsatteForDeltaker(deltaker)
        val navEnheter = navEnhetService.hentNavEnheterForDeltaker(deltaker)
        val sisteVurdering = SharedResponseMappers.hentSisteVurdering(
            vurderingRepository = vurderingRepository,
            deltakerId = deltaker.id,
        )

        val historikk = deltakerHistorikkService.getForDeltaker(
            deltakerId = deltaker.id,
            inkluderFullHistorikk = false,
        )

        return DeltakerResponse(
            id = deltaker.id,
            navBruker = buildNavBrukerResponse(
                navBruker = deltaker.navBruker,
                navEnheter = navEnheter,
                navAnsatte = navAnsatte,
            ),
            gjennomforing = buildGjennomforingResponse(
                gjennomforing = deltaker.gjennomforing,
                includePrisinfo = includePrisinfo,
                deltakerStatus = deltaker.status.type,
            ),
            startdato = deltaker.startdato,
            sluttdato = deltaker.sluttdato,
            dagerPerUke = deltaker.dagerPerUke,
            deltakelsesprosent = deltaker.deltakelsesprosent,
            bakgrunnsinformasjon = deltaker.bakgrunnsinformasjon,
            deltakelsesinnhold = deltaker.deltakelsesinnhold,
            status = deltaker.status,
            vedtaksinformasjon = deltaker.vedtaksinformasjon?.let {
                buildVedtaksinformasjonResponse(
                    vedtaksinformasjon = it,
                    navEnheter = navEnheter,
                    navAnsatte = navAnsatte,
                )
            },
            sistEndret = deltaker.sistEndret,
            kilde = deltaker.kilde,
            erManueltDeltMedArrangor = deltaker.erManueltDeltMedArrangor,
            opprettet = deltaker.opprettet,
            soktInnDato = deltakerRepository.getSoktInnDato(deltaker.id),
            deltakelsesmengder = buildDeltakelsesmengderResponse(deltaker, historikk),
            erLaastForEndringer = deltakerLaaseService.erLaastForEndringer(deltaker),
            endringsforslagFraArrangor = endringsforslagForDeltaker,
            sisteVurdering = sisteVurdering?.let { VurderingResponse.fromVurdering(it) },
            // vi trenger alltid DeltakerHistorikk.ImportertFraArena
            importertFraArena = historikk
                .filterIsInstance<DeltakerHistorikk.ImportertFraArena>()
                .let { it.firstOrNull()?.importertFraArena },
        )
    }

    internal fun buildDeltakelsesmengderResponse(
        deltaker: Deltaker,
        historikk: List<DeltakerHistorikk>,
    ): DeltakelsesmengderResponse? {
        // Vedtak, Arena-import og mengdeendringer gir mengdene. Endret oppstartsdato
        // fra Nav eller arrangør flytter periodenes start i historikkberegningen.
        val deltakelsesmengder = historikk.toDeltakelsesmengder()

        // Avgrens også mot deltakerens faktiske datoer: første mengde skal ikke
        // starte før oppstart, og endringer etter sluttdato skal ikke vises.
        val avgrensedeMengder = if (deltaker.startdato != null) {
            deltakelsesmengder.periode(deltaker.startdato, deltaker.sluttdato)
        } else {
            deltakelsesmengder
        }

        // Uten gyldige mengdeperioder returneres null for hele deltakelsesmengder-feltet.
        // Finnes en periode, bevares den selv om prosent og dager er tømt.
        val sisteMengde = avgrensedeMengder.lastOrNull()
        return if (sisteMengde == null) {
            null
        } else {
            DeltakelsesmengderResponse(
                // Bare senere endringer vises som neste periode. Mengden fra selve
                // oppstarten vises allerede fra deltakerresponsens toppnivåfelter.
                nesteDeltakelsesmengde = avgrensedeMengder
                    .nesteTilVisning(deltaker.startdato)
                    ?.let(DeltakelsesmengdeResponse::fromDeltakelsesmengde),
                // Endringsskjemaet trenger siste planlagte mengde selv om den ikke vises som «neste».
                sisteDeltakelsesmengde = DeltakelsesmengdeResponse.fromDeltakelsesmengde(sisteMengde),
            )
        }
    }

    internal fun buildGjennomforingResponse(
        gjennomforing: Gjennomforing,
        includePrisinfo: Boolean,
        deltakerStatus: DeltakerStatus.Type? = null,
    ): GjennomforingResponse {
        val prisinformasjon = GjennomforingPrisinformasjon.hent(
            gjennomforing = gjennomforing,
            includePrisinfo = includePrisinfo,
            deltakerStatus = deltakerStatus,
        )

        return SharedResponseMappers.buildGjennomforingResponse(
            gjennomforing = gjennomforing,
            opplaringKategoriseringValg = gjennomforing.opplaringKategorisering,
            prisinformasjon = prisinformasjon,
        )
    }

    internal fun buildVedtaksinformasjonResponse(
        vedtaksinformasjon: Vedtaksinformasjon,
        navEnheter: GenericCache<NavEnhet>,
        navAnsatte: GenericCache<NavAnsatt>,
    ): VedtaksinformasjonResponse = SharedResponseMappers.buildVedtaksinformasjonResponse(
        vedtaksinformasjon = vedtaksinformasjon,
        navEnheter = navEnheter,
        navAnsatte = navAnsatte,
    )

    internal suspend fun buildNavBrukerResponse(
        navBruker: NavBruker,
        navAnsatte: GenericCache<NavAnsatt>,
        navEnheter: GenericCache<NavEnhet>,
    ): NavBrukerResponse = SharedResponseMappers.buildNavBrukerResponse(
        navBruker = navBruker,
        navAnsatte = navAnsatte,
        navEnheter = navEnheter,
        erDigital = digitalBrukerService.erDigital(navBruker.personident),
    )
}
