package no.nav.amt.deltaker.api.response

import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.model.Deltakerliste
import no.nav.amt.deltaker.model.Vedtaksinformasjon
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.service.DeltakerHistorikkService
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.tiltaksarrangor.vurdering.VurderingRepository
import no.nav.amt.deltaker.veileder.DeltakerLaaseService
import no.nav.amt.internapi.deltaker.response.ArrangorResponse
import no.nav.amt.internapi.deltaker.response.DeltakerResponse
import no.nav.amt.internapi.deltaker.response.GjennomforingResponse
import no.nav.amt.internapi.deltaker.response.NavBrukerResponse
import no.nav.amt.internapi.deltaker.response.NavVeilederResponse
import no.nav.amt.internapi.deltaker.response.VedtaksinformasjonResponse
import no.nav.amt.internapi.deltaker.response.VurderingResponse
import no.nav.amt.lib.ktor.clients.distribusjon.AmtDistribusjonClient
import no.nav.amt.lib.models.arrangor.melding.Forslag
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
    suspend fun buildDeltakerResponse(deltaker: Deltaker): DeltakerResponse {
        // hent alle entries som behøver navn på Nav-ansatt eller -enhet
        val endringsforslagForDeltaker = forslagRepository
            .getForDeltaker(deltaker.id)
            .filter { it.status is Forslag.Status.VenterPaSvar }

        val navAnsatte = navAnsattService.hentNavAnsatteForDeltaker(deltaker)
        val navEnheter = navEnhetService.hentNavEnheterForDeltaker(deltaker)
        val sisteVurdering = vurderingRepository
            .getForDeltaker(deltaker.id)
            .maxByOrNull { it.gyldigFra }

        return DeltakerResponse(
            id = deltaker.id,
            navBruker = buildNavBrukerResponseFromNavBruker(
                navBruker = deltaker.navBruker,
                navEnheter = navEnheter,
                navAnsatte = navAnsatte,
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
                    navEnheter = navEnheter,
                    navAnsatte = navAnsatte,
                )
            },
            sistEndret = deltaker.sistEndret,
            kilde = deltaker.kilde,
            erManueltDeltMedArrangor = deltaker.erManueltDeltMedArrangor,
            opprettet = deltaker.opprettet,
            historikk = deltakerHistorikkService.getForDeltaker(deltaker.id),
            erLaastForEndringer = deltakerLaaseService.erLaastForEndringer(deltaker),
            endringsforslagFraArrangor = endringsforslagForDeltaker,
            prisinformasjon = deltaker.deltakerliste.prisinformasjon,
            sisteVurdering = sisteVurdering?.let { VurderingResponse.fromVurdering(it) },
        )
    }

    internal fun buildGjennomforingResponse(deltakerliste: Deltakerliste) = GjennomforingResponse(
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
                navn = arrangorService.getArrangorNavn(deltakerliste.arrangor),
                deltakerliste.arrangor.organisasjonsnummer,
            )
        },
        pameldingstype = deltakerliste.pameldingstype,
        type = deltakerliste.gjennomforingstype,
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
