package no.nav.amt.deltaker.bff.clients

import no.nav.amt.deltaker.bff.model.ArrangorModel
import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.deltaker.bff.model.GjennomforingModel
import no.nav.amt.deltaker.bff.model.NavBrukerModel
import no.nav.amt.deltaker.bff.model.VedtaksinformasjonModel
import no.nav.amt.internapi.deltaker.response.ArrangorResponse
import no.nav.amt.internapi.deltaker.response.DeltakerResponse
import no.nav.amt.internapi.deltaker.response.GjennomforingResponse
import no.nav.amt.internapi.deltaker.response.NavBrukerResponse
import no.nav.amt.internapi.deltaker.response.VedtaksinformasjonResponse

class ModelMapper {
    companion object {
        fun toDeltaker(deltakerResponse: DeltakerResponse) = with(deltakerResponse) {
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
                sistEndret = sistEndret,
                erManueltDeltMedArrangor = erManueltDeltMedArrangor,
                vedtaksinformasjon = vedtaksinformasjon?.let { toVedtaksinformasjon(it) },
                erLaastForEndringer = erLaastForEndringer,
                endringsforslagFraArrangor = endringsforslagFraArrangor,
                prisinformasjon = prisinformasjon,
                sisteVurdering = sisteVurdering,
                deltakelsesmengder = deltakelsesmengder,
                soktInnDato = soktInnDato,
                importertFraArena = importertFraArena,
            )
        }

        internal fun toNavBruker(navBrukerResponse: NavBrukerResponse) = with(navBrukerResponse) {
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

        internal fun toGjennomforing(gjennomforingResponse: GjennomforingResponse) = with(gjennomforingResponse) {
            GjennomforingModel(
                id = id,
                type = type,
                tiltak = tiltakstype,
                navn = navn,
                status = status,
                startDato = startDato,
                sluttDato = sluttDato,
                oppstart = oppstart,
                apentForPamelding = apentForPamelding,
                oppmoteSted = oppmoteSted,
                arrangor = arrangor?.let { toArrangor(it) },
                pameldingstype = pameldingstype,
                prisinformasjon = prisinformasjon,
                utflatetKodeverk = utflatetKodeverk,
            )
        }

        internal fun toArrangor(arrangorResponse: ArrangorResponse) = ArrangorModel(
            navn = arrangorResponse.navn,
            organisasjonsnummer = arrangorResponse.organisasjonsnummer,
        )

        internal fun toVedtaksinformasjon(vedtaksinformasjonResponse: VedtaksinformasjonResponse) = with(vedtaksinformasjonResponse) {
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
