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
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.response.ArrangorResponse
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.response.DeltakerResponse
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.response.GjennomforingResponse
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.response.NavBrukerResponse
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.response.VedtaksinformasjonResponse
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import java.util.UUID

class ResponseBuilder(
    private val arrangorService: ArrangorService,
    private val navAnsattRepository: NavAnsattRepository,
    private val navEnhetRepository: NavEnhetRepository,
    private val amtDistribusjonClient: AmtDistribusjonClient,
    private val deltakerHistorikkService: DeltakerHistorikkService,
    private val forslagRepository: ForslagRepository,
    private val deltakerLaaseService: DeltakerLaaseService,
) {
    data class GenericCache<T>(
        private val cacheName: String,
        private val itemMap: Map<UUID, T>,
    ) {
        constructor(
            cacheName: String,
            items: List<T>,
            idSelector: (T) -> UUID,
        ) : this(
            cacheName = cacheName,
            itemMap = items.associateBy(idSelector),
        )

        fun getOrThrow(id: UUID): T = itemMap[id]
            ?: throw NoSuchElementException("Fant ikke entry med id $id i cache $cacheName")
    }

    suspend fun buildDeltakerResponse(deltaker: Deltaker): DeltakerResponse {
        val navAnsattCache = GenericCache(
            cacheName = "navAnsattCache",
            items = navAnsattRepository.getManyById(
                ider = setOfNotNull(
                    deltaker.navBruker.navVeilederId,
                    deltaker.vedtaksinformasjon?.opprettetAv,
                    deltaker.vedtaksinformasjon?.sistEndretAv,
                ),
            ),
            idSelector = NavAnsatt::id,
        )

        val navEnhetCache = GenericCache(
            cacheName = "navEnhetCache",
            items = navEnhetRepository.getMany(
                setOfNotNull(
                    deltaker.navBruker.navEnhetId,
                    deltaker.vedtaksinformasjon?.opprettetAvEnhet,
                    deltaker.vedtaksinformasjon?.sistEndretAvEnhet,
                ),
            ),
            idSelector = NavEnhet::id,
        )

        return DeltakerResponse(
            id = deltaker.id,
            navBruker = buildNavBrukerResponseFromNavBruker(
                navBruker = deltaker.navBruker,
                navAnsattCache = navAnsattCache,
                navEnhetCache = navEnhetCache,
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
                    navAnsattCache = navAnsattCache,
                    navEnhetCache = navEnhetCache,
                )
            },
            sistEndret = deltaker.sistEndret,
            kilde = deltaker.kilde,
            erManueltDeltMedArrangor = deltaker.erManueltDeltMedArrangor,
            opprettet = deltaker.opprettet,
            historikk = deltakerHistorikkService.getForDeltaker(deltaker.id),
            erLaastForEndringer = deltakerLaaseService.erLaastForEndringer(deltaker),
            endringsforslagFraArrangor = forslagRepository.getForDeltaker(deltaker.id),
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
        arrangor = ArrangorResponse(
            navn = arrangorService.getArrangorNavn(deltakerliste.arrangor),
            deltakerliste.arrangor.organisasjonsnummer,
        ),
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
        navAnsattCache: GenericCache<NavAnsatt>,
        navEnhetCache: GenericCache<NavEnhet>,
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
        navVeileder = navBruker.navVeilederId?.let { navAnsattCache.getOrThrow(it).navn },
        navEnhet = navBruker.navEnhetId?.let { navEnhetCache.getOrThrow(it).navn },
    )
}
