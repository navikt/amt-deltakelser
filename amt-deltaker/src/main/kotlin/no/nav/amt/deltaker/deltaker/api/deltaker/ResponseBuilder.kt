package no.nav.amt.deltaker.deltaker.api.deltaker

import no.nav.amt.deltaker.apiclients.distribusjon.AmtDistribusjonClient
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.DeltakerLaaseService
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.Vedtaksinformasjon
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.internapi.deltaker.response.ArrangorResponse
import no.nav.amt.internapi.deltaker.response.DeltakerResponse
import no.nav.amt.internapi.deltaker.response.GjennomforingResponse
import no.nav.amt.internapi.deltaker.response.NavBrukerResponse
import no.nav.amt.internapi.deltaker.response.VedtaksinformasjonResponse
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.ForslagDecorator
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.utils.GenericCache

class ResponseBuilder(
    private val arrangorService: ArrangorService,
    private val navAnsattRepository: NavAnsattRepository,
    private val navEnhetRepository: NavEnhetRepository,
    private val amtDistribusjonClient: AmtDistribusjonClient,
    private val deltakerHistorikkService: DeltakerHistorikkService,
    private val forslagRepository: ForslagRepository,
    private val deltakerLaaseService: DeltakerLaaseService,
) {
    suspend fun buildDeltakerResponse(deltaker: Deltaker): DeltakerResponse {
        // hent endringsforslag
        val endringsforslag = forslagRepository.getForDeltaker(deltaker.id)

        // hent alle entries som behøver navn på Nav-ansatt eller -enhet
        val avvistAvNavAnsatte = endringsforslag
            .map { it.status }
            .filterIsInstance<Forslag.Status.Avvist>()
            .map { it.avvistAv }

        // hent alle Nav-ansatte i kontekst
        val navAnsatte = GenericCache(
            cacheName = "navAnsattCache",
            items = navAnsattRepository.getManyById(
                ider = setOfNotNull(
                    deltaker.navBruker.navVeilederId,
                    deltaker.vedtaksinformasjon?.opprettetAv,
                    deltaker.vedtaksinformasjon?.sistEndretAv,
                ).plus(avvistAvNavAnsatte.map { it.id }),
            ),
            idSelector = NavAnsatt::id,
        )

        // hent alle Nav-enheter i kontekst
        val navEnheter = GenericCache(
            cacheName = "navEnhetCache",
            items = navEnhetRepository.getMany(
                setOfNotNull(
                    deltaker.navBruker.navEnhetId,
                    deltaker.vedtaksinformasjon?.opprettetAvEnhet,
                    deltaker.vedtaksinformasjon?.sistEndretAvEnhet,
                ).plus(avvistAvNavAnsatte.map { it.enhetId }),
            ),
            idSelector = NavEnhet::id,
        )

        // pakk inn endringsforslag i dekorert format
        val dekorerteEndringsforslag = endringsforslag.map {
            when (val status = it.status) {
                is Forslag.Status.Avvist -> {
                    ForslagDecorator.AvvistStatusDecorator(
                        forslag = it,
                        avvistAvAnsattNavn = navAnsatte.getOrThrow(status.avvistAv.id).navn,
                        avvistAvEnhetNavn = navEnheter.getOrThrow(status.avvistAv.enhetId).navn,
                    )
                }

                else -> ForslagDecorator.DefaultDecorator(it)
            }
        }

        return DeltakerResponse(
            id = deltaker.id,
            navBruker = buildNavBrukerResponseFromNavBruker(
                navBruker = deltaker.navBruker,
                navAnsatte = navAnsatte,
                navEnheter = navEnheter,
            ),
            gjennomforing = buildGjennomforingResponse(deltaker.deltakerliste),
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
                    navAnsattCache = navAnsatte,
                    navEnhetCache = navEnheter,
                )
            },
            sistEndret = deltaker.sistEndret,
            kilde = deltaker.kilde,
            erManueltDeltMedArrangor = deltaker.erManueltDeltMedArrangor,
            opprettet = deltaker.opprettet,
            historikk = deltakerHistorikkService.getForDeltaker(deltaker.id),
            erLaastForEndringer = deltakerLaaseService.erLaastForEndringer(deltaker),
            endringsforslagFraArrangor = dekorerteEndringsforslag,
        )
    }

    internal fun buildGjennomforingResponse(deltakerliste: Deltakerliste) = GjennomforingResponse(
        id = deltakerliste.id,
        tiltakstype = deltakerliste.tiltakstype,
        navn = deltakerliste.navn,
        status = deltakerliste.status,
        startDato = deltakerliste.startDato,
        sluttDato = deltakerliste.sluttDato,
        oppstart = deltakerliste.oppstart,
        apentForPamelding = deltakerliste.apentForPamelding,
        oppmoteSted = deltakerliste.oppmoteSted,
        arrangor = deltakerliste.arrangor?.let {
            ArrangorResponse(
                navn = arrangorService.getArrangorNavn(deltakerliste.arrangor),
                deltakerliste.arrangor.organisasjonsnummer,
            )
        },
        pameldingstype = deltakerliste.pameldingstype,
    )

    internal fun buildVedtaksinformasjonResponse(
        vedtaksinformasjon: Vedtaksinformasjon,
        navAnsattCache: GenericCache<NavAnsatt>,
        navEnhetCache: GenericCache<NavEnhet>,
    ) = VedtaksinformasjonResponse(
        fattet = vedtaksinformasjon.fattet,
        fattetAvNav = vedtaksinformasjon.fattetAvNav,
        opprettet = vedtaksinformasjon.opprettet,
        opprettetAv = navAnsattCache.getOrThrow(vedtaksinformasjon.opprettetAv).navn,
        opprettetAvEnhet = navEnhetCache.getOrThrow(vedtaksinformasjon.opprettetAvEnhet).navn,
        sistEndret = vedtaksinformasjon.sistEndret,
        sistEndretAv = navAnsattCache.getOrThrow(vedtaksinformasjon.sistEndretAv).navn,
        sistEndretAvEnhet = navEnhetCache.getOrThrow(vedtaksinformasjon.sistEndretAvEnhet).navn,
    )

    internal suspend fun buildNavBrukerResponseFromNavBruker(
        navBruker: NavBruker,
        navAnsatte: GenericCache<NavAnsatt>,
        navEnheter: GenericCache<NavEnhet>,
    ) = NavBrukerResponse(
        personident = navBruker.personident,
        fornavn = navBruker.fornavn,
        mellomnavn = navBruker.mellomnavn,
        etternavn = navBruker.etternavn,
        telefon = navBruker.telefon,
        epost = navBruker.epost,
        erSkjermet = navBruker.erSkjermet,
        adresse = navBruker.adresse,
        adressebeskyttelse = navBruker.adressebeskyttelse,
        oppfolgingsperioder = navBruker.oppfolgingsperioder,
        innsatsgruppe = navBruker.innsatsgruppe,
        erDigital = amtDistribusjonClient.digitalBruker(navBruker.personident),
        navVeileder = navBruker.navVeilederId?.let { navAnsatte.getOrThrow(it).navn },
        navEnhet = navBruker.navEnhetId?.let { navEnheter.getOrThrow(it).navn },
    )
}
