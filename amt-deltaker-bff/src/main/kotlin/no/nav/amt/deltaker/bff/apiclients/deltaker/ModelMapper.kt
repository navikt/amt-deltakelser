package no.nav.amt.deltaker.bff.apiclients.deltaker

import no.nav.amt.deltaker.bff.deltaker.model.ArrangorModel
import no.nav.amt.deltaker.bff.deltaker.model.DeltakerModel
import no.nav.amt.deltaker.bff.deltaker.model.GjennomforingModel
import no.nav.amt.deltaker.bff.deltaker.model.NavBrukerModel
import no.nav.amt.deltaker.bff.deltaker.model.VedtaksinformasjonModel
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.response.ArrangorResponse
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.response.DeltakerResponse
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.response.GjennomforingResponse
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.response.NavBrukerResponse
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.response.VedtaksinformasjonResponse

class ModelMapper {
    companion object {
        fun toDeltaker(deltakerResponse: DeltakerResponse) =
            with(deltakerResponse) {
                DeltakerModel(
                    id = id,
                    navBruker = toNavBruker(navBruker),
                    startdato = startdato,
                    gjennomforing = toGjennomforing(gjennomforing),
                    sluttdato = sluttdato,
                    dagerPerUke = dagerPerUke,
                    deltakelsesprosent = deltakelsesprosent,
                    bakgrunnsinformasjon = bakgrunnsinformasjon,
                    deltakelsesinnhold = deltakelsesinnhold,
                    status = status,
                    kanEndres = !erLaastForEndringer,
                    sistEndret = sistEndret,
                    erManueltDeltMedArrangor = erManueltDeltMedArrangor,
                    historikk = historikk,
                    vedtaksinformasjon = vedtaksinformasjon?.let { toVedtaksinformasjon(it) },
                    erLaastForEndringer = erLaastForEndringer,
                    endringsforslagFraArrangor = endringsforslagFraArrangor,
                )
            }

        internal fun toNavBruker(navBrukerResponse: NavBrukerResponse) =
            with(navBrukerResponse) {
                NavBrukerModel(
                    personident = personident,
                    fornavn = fornavn,
                    mellomnavn = mellomnavn,
                    etternavn = etternavn,
                    navVeileder = navVeileder,
                    navEnhet = navEnhet,
                    telefon = telefon,
                    epost = epost,
                    erSkjermet = erSkjermet,
                    adresse = adresse,
                    adressebeskyttelse = adressebeskyttelse,
                    oppfolgingsperioder = oppfolgingsperioder,
                    innsatsgruppe = innsatsgruppe,
                    erDigital = erDigital,
                )
            }

        internal fun toGjennomforing(gjennomforingResponse: GjennomforingResponse) =
            with(gjennomforingResponse) {
                GjennomforingModel(
                    id = id,
                    tiltak = tiltakstype,
                    navn = navn,
                    status = status,
                    startDato = startDato,
                    sluttDato = sluttDato,
                    oppstart = oppstart,
                    apentForPamelding = apentForPamelding,
                    oppmoteSted = oppmoteSted,
                    arrangor = toArrangor(arrangor),
                    pameldingstype = pameldingstype,
                )
            }

        internal fun toArrangor(arrangorResponse: ArrangorResponse) = ArrangorModel(arrangorResponse.navn)

        internal fun toVedtaksinformasjon(vedtaksinformasjonResponse: VedtaksinformasjonResponse) =
            with(vedtaksinformasjonResponse) {
                VedtaksinformasjonModel(
                    fattet = fattet,
                    fattetAvNav = fattetAvNav,
                    opprettet = opprettet,
                    opprettetAv = opprettetAv,
                    opprettetAvEnhet = opprettetAvEnhet,
                    sistEndret = sistEndret,
                    sistEndretAv = sistEndretAv,
                    sistEndretAvEnhet = sistEndretAvEnhet,
                )
            }
    }
}
