package no.nav.amt.deltaker.api.response

import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.model.Deltakerliste
import no.nav.amt.deltaker.model.Vedtaksinformasjon
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.repository.KodeverkValgRepository
import no.nav.amt.deltaker.service.DeltakerHistorikkService
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.tiltaksarrangor.vurdering.VurderingRepository
import no.nav.amt.deltaker.veileder.DeltakerLaaseService
import no.nav.amt.internapi.deltaker.response.ArrangorResponse
import no.nav.amt.internapi.deltaker.response.DeltakelsesmengdeResponse
import no.nav.amt.internapi.deltaker.response.DeltakelsesmengderResponse
import no.nav.amt.internapi.deltaker.response.DeltakerResponse
import no.nav.amt.internapi.deltaker.response.GjennomforingResponse
import no.nav.amt.internapi.deltaker.response.NavBrukerResponse
import no.nav.amt.internapi.deltaker.response.NavVeilederResponse
import no.nav.amt.internapi.deltaker.response.VedtaksinformasjonResponse
import no.nav.amt.internapi.deltaker.response.VurderingResponse
import no.nav.amt.lib.ktor.clients.distribusjon.AmtDistribusjonClient
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengder
import no.nav.amt.lib.models.deltaker.extensions.getInnsoktDato
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.utils.GenericCache

class ResponseBuilder(
    private val arrangorService: ArrangorService,
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
    private val amtDistribusjonClient: AmtDistribusjonClient,
    private val deltakerHistorikkService: DeltakerHistorikkService,
    private val forslagRepository: ForslagRepository,
    private val deltakerLaaseService: DeltakerLaaseService,
    private val vurderingRepository: VurderingRepository,
) {
    suspend fun buildDeltakerResponse(
        deltaker: Deltaker,
        includeKodeverk: Boolean = false,
    ): DeltakerResponse {
        // Forslag hentes også fra databasen med historikk men historikk filtrerer bort statusen
        // som ønskes her.
        val endringsforslagForDeltaker = forslagRepository
            .getForDeltaker(deltaker.id)
            .filter { it.status is Forslag.Status.VenterPaSvar }

        val navAnsatte = navAnsattService.hentNavAnsatteForDeltaker(deltaker)
        val navEnheter = navEnhetService.hentNavEnheterForDeltaker(deltaker)
        val sisteVurdering = vurderingRepository
            .getForDeltaker(deltaker.id)
            .maxByOrNull { it.gyldigFra }

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
                includeKodeverk = includeKodeverk,
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
            // hvis DeltakerHistorikk.ImportertFraArena finnes, trenger vi ikke
            // DeltakerHistorikk.InnsokPaaFellesOppstart eller DeltakerHistorikk.Vedtak
            //
            // hvis DeltakerHistorikk.ImportertFraArena ikke finnes, trenger vi
            // DeltakerHistorikk.InnsokPaaFellesOppstart
            //
            // hvis DeltakerHistorikk.InnsokPaaFellesOppstart ikke finnes, trenger vi
            // vi DeltakerHistorikk.Vedtak
            soktInnDato = historikk.getInnsoktDato()?.toLocalDate(),
            // koden antyder at vi trenger disse her for deltakelsesmengder:
            // DeltakerHistorikk.ImportertFraArena
            // DeltakerHistorikk.Endring
            // DeltakerHistorikk.Vedtak
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
        includeKodeverk: Boolean,
    ) = GjennomforingResponse(
        id = deltakerliste.id,
        tiltakstype = deltakerliste.tiltakstype,
        navn = deltakerliste.navn,
        status = deltakerliste.status,
        startDato = deltakerliste.startDato,
        sluttDato = deltakerliste.sluttDato,
        antallPlasser = deltakerliste.antallPlasser,
        oppstart = deltakerliste.oppstart,
        apentForPamelding = deltakerliste.apentForPamelding,
        oppmoteSted = deltakerliste.oppmoteSted,
        arrangor = deltakerliste.arrangor?.let {
            ArrangorResponse(
                navn = arrangorService.getArrangorNavn(
                    arrangor = deltakerliste.arrangor,
                    gjennomforingstype = deltakerliste.gjennomforingstype,
                ),
                deltakerliste.arrangor.organisasjonsnummer,
            )
        },
        pameldingstype = deltakerliste.pameldingstype,
        type = deltakerliste.gjennomforingstype,
        kodeverkValg = if (includeKodeverk && deltakerliste.gjennomforingstype == GjennomforingType.Enkeltplass) {
            KodeverkValgRepository.hentKodeverkValg(deltakerliste.id)
        } else {
            emptySet()
        },
    )

    internal fun buildVedtaksinformasjonResponse(
        vedtaksinformasjon: Vedtaksinformasjon,
        navEnheter: GenericCache<NavEnhet>,
        navAnsatte: GenericCache<NavAnsatt>,
    ) = VedtaksinformasjonResponse(
        fattet = vedtaksinformasjon.fattet,
        fattetAvNav = vedtaksinformasjon.fattetAvNav,
        opprettet = vedtaksinformasjon.opprettet,
        opprettetAv = navAnsatte.getOrThrow(vedtaksinformasjon.opprettetAv).navn,
        opprettetAvEnhet = navEnheter.getOrThrow(vedtaksinformasjon.opprettetAvEnhet).navn,
        sistEndret = vedtaksinformasjon.sistEndret,
        sistEndretAv = navAnsatte.getOrThrow(vedtaksinformasjon.sistEndretAv).navn,
        sistEndretAvEnhet = navEnheter.getOrThrow(vedtaksinformasjon.sistEndretAvEnhet).navn,
    )

    internal suspend fun buildNavBrukerResponse(
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
        navVeileder = navBruker.navVeilederId
            ?.let { navAnsatte.getOrThrow(it) }
            ?.let { veileder ->
                NavVeilederResponse(
                    navn = veileder.navn,
                    epost = veileder.epost,
                    telefonnummer = veileder.telefon,
                )
            },
        navEnhet = navBruker.navEnhetId?.let { navEnheter.getOrThrow(it).navn },
    )
}
