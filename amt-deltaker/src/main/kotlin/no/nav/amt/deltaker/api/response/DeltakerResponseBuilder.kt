package no.nav.amt.deltaker.api.response

import no.nav.amt.deltaker.digitalbruker.DigitalBrukerService
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.model.Deltakerliste
import no.nav.amt.deltaker.model.Vedtaksinformasjon
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.deltaker.service.DeltakerHistorikkService
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
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
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengder
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.utils.GenericCache
import java.util.UUID

class DeltakerResponseBuilder(
    private val arrangorService: ArrangorService,
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
        includeOpplaringKategorisering: Boolean = true,
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
            id = deltaker.id,
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
                deltakerliste = deltaker.deltakerliste,
                includeOpplaringKategorisering = includeOpplaringKategorisering,
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
            // Følgende er påkrevd for å beregne deltakelsesmengder
            // DeltakerHistorikk.ImportertFraArena
            // DeltakerHistorikk.Endring
            // DeltakerHistorikk.Vedtak
            // EndringFraArrangor.LeggTilOppstartsdato
            deltakelsesmengder = historikk
                .toDeltakelsesmengder()
                .let { mengder ->
                    // Usikker på hva dette handler om men koden er kopiert fra Deltaker
                    deltaker.startdato?.let { mengder.periode(it, deltaker.sluttdato) } ?: mengder
                }.let { deltakelsesmengder ->
                    DeltakelsesmengderResponse(
                        nesteDeltakelsesmengde = deltakelsesmengder.nesteGjeldende?.let(DeltakelsesmengdeResponse::fromDeltakelsesmengde),
                        sisteDeltakelsesmengde = deltakelsesmengder.lastOrNull()?.let(DeltakelsesmengdeResponse::fromDeltakelsesmengde),
                    )
                },
            erLaastForEndringer = deltakerLaaseService.erLaastForEndringer(deltaker),
            endringsforslagFraArrangor = endringsforslagForDeltaker,
            prisinformasjon = deltaker.deltakerliste.prisinformasjon,
            sisteVurdering = sisteVurdering?.let { VurderingResponse.fromVurdering(it) },
            // vi trenger alltid DeltakerHistorikk.ImportertFraArena
            importertFraArena = historikk
                .filterIsInstance<DeltakerHistorikk.ImportertFraArena>()
                .let { it.firstOrNull()?.importertFraArena },
        )
    }

    internal fun buildGjennomforingResponse(
        deltakerliste: Deltakerliste,
        includeOpplaringKategorisering: Boolean,
    ): GjennomforingResponse {
        val skalHenteEnkeltplassValg = includeOpplaringKategorisering && deltakerliste.nyForskriftOpplaring

        val (prisinformasjon, prisinformasjonTilGodkjenning) = if (skalHenteEnkeltplassValg) {
            hentPrisinfoPair(deltakerliste.id)
        } else {
            Pair(null, null)
        }

        return SharedResponseMappers.buildGjennomforingResponse(
            deltakerliste = deltakerliste,
            arrangorService = arrangorService,
            opplaringKategoriseringValg = deltakerliste.opplaringKategorisering,
            prisinformasjon = prisinformasjon,
            prisinformasjonTilGodkjenning = prisinformasjonTilGodkjenning,
        )
    }

    /**
     * Henter gjeldende- og prisinfo til endring.
     *
     * For deltakerstatuser SOKT_INN og senere, skal det alltid finnes en gjeldende prisinfo.
     * first i pair vil da inneholde gjeldende prisinfo, og second vil inneholde endring om det finnes.
     *
     * For deltakerstatuser KLADD og UTKAST, skal det kun finnes prisinfo til godkjenning (ENDRING)
     * first i pair vil da inneholde endring og second vil alltid inneholde null.
     *
     * @param gjennomforingId Deltakerliste-ID
     */
    internal fun hentPrisinfoPair(gjennomforingId: UUID): Pair<PrisinformasjonDto?, PrisinformasjonDto?> {
        val prisinfoMap = PrisinfoRepoAdapter.hentPrisinfoMap(gjennomforingId)

        if (prisinfoMap.isEmpty()) return Pair(null, null)

        val gjeldendePrisinfo = prisinfoMap[PrisinfoDbo.Rolle.GJELDENDE]
        val prisinfoTilGodkjenning = prisinfoMap[PrisinfoDbo.Rolle.ENDRING]

        return if (gjeldendePrisinfo == null) {
            // deltakerstatus er KLADD eller UTKAST
            Pair(prisinfoTilGodkjenning, null)
        } else {
            // deltakerstatus er SOKT_INN eller senere
            // skal alltid ha gjeldendePrisinfo
            Pair(gjeldendePrisinfo, prisinfoTilGodkjenning)
        }
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
