package no.nav.amt.deltaker.deltaker.api.deltaker

import no.nav.amt.deltaker.apiclients.distribusjon.AmtDistribusjonClient
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.Vedtaksinformasjon
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
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
    private val navEnhetService: NavEnhetService,
    private val amtDistribusjonClient: AmtDistribusjonClient,
    private val deltakerHistorikkService: DeltakerHistorikkService,
    private val forslagRepository: ForslagRepository,
) {
    suspend fun buildDeltakerResponse(deltaker: Deltaker): DeltakerResponse {
        val arrangorNavn = arrangorService.getArrangorNavn(deltaker.deltakerliste.arrangor)
        // Flyttet as is fra deltaker-bff. Kan dette gjøres asynkront?
        val erDigital = amtDistribusjonClient.digitalBruker(deltaker.navBruker.personident)
        val historikk = deltakerHistorikkService.getForDeltaker(deltaker.id)
        val forslag = forslagRepository.getForDeltaker(deltaker.id)
        return DeltakerResponse(
            id = deltaker.id,
            navBruker = fromNavBruker(deltaker.navBruker, erDigital),
            gjennomforing = GjennomforingResponse(
                id = deltaker.deltakerliste.id,
                tiltakstype = deltaker.deltakerliste.tiltakstype,
                navn = deltaker.deltakerliste.navn,
                status = deltaker.deltakerliste.status,
                startDato = deltaker.deltakerliste.startDato,
                sluttDato = deltaker.deltakerliste.sluttDato,
                oppstart = deltaker.deltakerliste.oppstart,
                apentForPamelding = deltaker.deltakerliste.apentForPamelding,
                oppmoteSted = deltaker.deltakerliste.oppmoteSted,
                arrangor = ArrangorResponse(
                    navn = arrangorNavn,
                    deltaker.deltakerliste.arrangor.organisasjonsnummer,
                ),
                pameldingstype = deltaker.deltakerliste.pameldingstype,
            ),
            startdato = deltaker.startdato,
            sluttdato = deltaker.sluttdato,
            dagerPerUke = deltaker.dagerPerUke,
            deltakelsesprosent = deltaker.deltakelsesprosent,
            bakgrunnsinformasjon = deltaker.bakgrunnsinformasjon,
            deltakelsesinnhold = deltaker.deltakelsesinnhold,
            status = deltaker.status,
            vedtaksinformasjon = buildVedtaksinformasjonResponse(deltaker.vedtaksinformasjon),
            sistEndret = deltaker.sistEndret,
            kilde = deltaker.kilde,
            erManueltDeltMedArrangor = deltaker.erManueltDeltMedArrangor,
            opprettet = deltaker.opprettet,
            historikk = historikk,
            erLaastForEndringer = true, // TODO(),
            endringsforslagFraArrangor = forslag,
        )
    }

    private fun buildVedtaksinformasjonResponse(vedtaksinformasjon: Vedtaksinformasjon?): VedtaksinformasjonResponse? {
        if (vedtaksinformasjon == null) return null
        val vedtakOpprettetAv = vedtaksinformasjon.let { navAnsattRepository.get(vedtaksinformasjon.opprettetAv) }
        val vedtakSistEndretAv = vedtaksinformasjon.let { navAnsattRepository.get(vedtaksinformasjon.sistEndretAv) }
        val enheter = navEnhetService.getEnheter(setOf(vedtaksinformasjon.opprettetAvEnhet, vedtaksinformasjon.sistEndretAvEnhet))

        return VedtaksinformasjonResponse(
            fattet = vedtaksinformasjon.fattet,
            fattetAvNav = vedtaksinformasjon.fattetAvNav,
            opprettet = vedtaksinformasjon.opprettet,
            opprettetAv = vedtakOpprettetAv?.navn ?: throw IllegalStateException("Fant ikke opprettet av navansatt"),
            opprettetAvEnhet =
                enheter[vedtaksinformasjon.opprettetAvEnhet]?.navn ?: throw IllegalStateException("Fant ikke opprettet av enhet"),
            sistEndret = vedtaksinformasjon.sistEndret,
            sistEndretAv = vedtakSistEndretAv?.navn,
            sistEndretAvEnhet = enheter.get(vedtaksinformasjon.sistEndretAvEnhet)?.navn,
        )
    }

    private fun fromNavBruker(navBruker: NavBruker, erDigital: Boolean): NavBrukerResponse = NavBrukerResponse(
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
        erDigital = erDigital,
        navVeileder = "", // TODO(),
        navEnhet = "", // TODO(),
    )
}
