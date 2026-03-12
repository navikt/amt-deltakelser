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
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.response.ArrangorResponse
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.response.DeltakerResponse
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.response.GjennomforingResponse
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.response.NavBrukerResponse
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.response.VedtaksinformasjonResponse
import no.nav.amt.lib.models.person.NavBruker

class ResponseBuilder(
    private val arrangorService: ArrangorService,
    private val navAnsattRepository: NavAnsattRepository,
    private val navEnhetRepository: NavEnhetRepository,
    private val navEnhetService: NavEnhetService,
    private val amtDistribusjonClient: AmtDistribusjonClient,
    private val deltakerHistorikkService: DeltakerHistorikkService,
    private val forslagRepository: ForslagRepository,
    private val deltakerLaaseService: DeltakerLaaseService,
) {
    suspend fun buildDeltakerResponse(deltaker: Deltaker): DeltakerResponse = DeltakerResponse(
        id = deltaker.id,
        navBruker = buildNavBrukerResponseFromNavBruker(navBruker = deltaker.navBruker),
        gjennomforing = buildGjennomforingResponse(deltaker.deltakerliste),
        startdato = deltaker.startdato,
        sluttdato = deltaker.sluttdato,
        dagerPerUke = deltaker.dagerPerUke,
        deltakelsesprosent = deltaker.deltakelsesprosent,
        bakgrunnsinformasjon = deltaker.bakgrunnsinformasjon,
        deltakelsesinnhold = deltaker.deltakelsesinnhold,
        status = deltaker.status,
        vedtaksinformasjon = deltaker.vedtaksinformasjon?.let { buildVedtaksinformasjonResponse(it) },
        sistEndret = deltaker.sistEndret,
        kilde = deltaker.kilde,
        erManueltDeltMedArrangor = deltaker.erManueltDeltMedArrangor,
        opprettet = deltaker.opprettet,
        historikk = deltakerHistorikkService.getForDeltaker(deltaker.id),
        erLaastForEndringer = deltakerLaaseService.erLaastForEndringer(deltaker),
        endringsforslagFraArrangor = forslagRepository.getForDeltaker(deltaker.id),
    )

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

    internal fun buildVedtaksinformasjonResponse(vedtaksinformasjon: Vedtaksinformasjon): VedtaksinformasjonResponse {
        val vedtakOpprettetAvAnsattNavn = navAnsattRepository.get(vedtaksinformasjon.opprettetAv)?.navn
            ?: throw IllegalStateException("Fant ikke opprettet av Nav-ansatt ${vedtaksinformasjon.opprettetAv} for vedtaksinformasjon")

        val enheter = navEnhetService.getEnheter(
            setOf(
                vedtaksinformasjon.opprettetAvEnhet,
                vedtaksinformasjon.sistEndretAvEnhet,
            ),
        )

        val opprettetAvEnhetNavn = enheter[vedtaksinformasjon.opprettetAvEnhet]?.navn
            ?: throw IllegalStateException("Fant ikke opprettet av Nav-enhet ${vedtaksinformasjon.opprettetAvEnhet} for vedtaksinformasjon")

        return VedtaksinformasjonResponse(
            fattet = vedtaksinformasjon.fattet,
            fattetAvNav = vedtaksinformasjon.fattetAvNav,
            opprettet = vedtaksinformasjon.opprettet,
            opprettetAv = vedtakOpprettetAvAnsattNavn,
            opprettetAvEnhet = opprettetAvEnhetNavn,
            sistEndret = vedtaksinformasjon.sistEndret,
            sistEndretAv = navAnsattRepository.get(vedtaksinformasjon.sistEndretAv)?.navn,
            sistEndretAvEnhet = enheter[vedtaksinformasjon.sistEndretAvEnhet]?.navn,
        )
    }

    internal suspend fun buildNavBrukerResponseFromNavBruker(navBruker: NavBruker): NavBrukerResponse = NavBrukerResponse(
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
        navVeileder = navBruker.navVeilederId?.let {
            navAnsattRepository.get(it)?.navn ?: throw IllegalStateException("Fant ikke Nav-veileder $it for innbygger")
        },
        navEnhet = navBruker.navEnhetId?.let {
            navEnhetRepository.get(it)?.navn ?: throw IllegalStateException("Fant ikke Nav-enhet $it for innbygger")
        },
    )
}
