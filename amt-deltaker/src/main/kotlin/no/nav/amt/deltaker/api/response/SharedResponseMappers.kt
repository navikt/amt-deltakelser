package no.nav.amt.deltaker.api.response

import no.nav.amt.deltaker.model.Deltakerliste
import no.nav.amt.deltaker.model.Vedtaksinformasjon
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.tiltaksarrangor.vurdering.VurderingRepository
import no.nav.amt.internapi.deltaker.response.ArrangorResponse
import no.nav.amt.internapi.deltaker.response.GjennomforingResponse
import no.nav.amt.internapi.deltaker.response.NavBrukerResponse
import no.nav.amt.internapi.deltaker.response.NavVeilederResponse
import no.nav.amt.internapi.deltaker.response.VedtaksinformasjonResponse
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.Vurdering
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.utils.GenericCache
import java.util.UUID

/**
 * Felles, rene mapper-funksjoner som brukes av både [DeltakerResponseBuilder] og [TiltakskoordinatorResponseBuilder].
 *
 * Funksjonene tar inn ferdig oppslåtte verdier (caches, `erDigital`, `kodeverkValg` osv.) slik at
 * de ikke har avhengigheter til services. Orkestrering av oppslag forblir i den enkelte builder.
 */
internal object SharedResponseMappers {
    fun buildNavBrukerResponse(
        navBruker: NavBruker,
        navAnsatte: GenericCache<NavAnsatt>,
        navEnheter: GenericCache<NavEnhet>,
        erDigital: Boolean,
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
        erDigital = erDigital,
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

    fun buildGjennomforingResponse(
        deltakerliste: Deltakerliste,
        arrangorService: ArrangorService,
        kodeverkValg: Set<UUID>,
        sertifiseringValg: Set<SertifiseringValg>,
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
        arrangor = deltakerliste.arrangor?.let { arrangor ->
            ArrangorResponse(
                // TODO: fjerne avhengighet til service?
                navn = arrangorService.getArrangorNavn(
                    arrangor = arrangor,
                    gjennomforingstype = deltakerliste.gjennomforingstype,
                ),
                organisasjonsnummer = arrangor.organisasjonsnummer,
            )
        },
        pameldingstype = deltakerliste.pameldingstype,
        type = deltakerliste.gjennomforingstype,
        kodeverkValg = kodeverkValg,
        sertifiseringValg = sertifiseringValg,
    )

    fun buildVedtaksinformasjonResponse(
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

    fun hentEndringsforslagVenterPaSvar(
        // TODO: fjerne avhengighet til repository?
        forslagRepository: ForslagRepository,
        deltakerId: UUID,
    ): List<Forslag> = forslagRepository
        .getForDeltaker(deltakerId)
        .filter { it.status is Forslag.Status.VenterPaSvar }

    fun hentSisteVurdering(
        // TODO: fjerne avhengighet til repository?
        vurderingRepository: VurderingRepository,
        deltakerId: UUID,
    ): Vurdering? = vurderingRepository
        .getForDeltaker(deltakerId)
        .maxByOrNull { it.gyldigFra }
}
