package no.nav.amt.deltaker.bff.navtiltakskoordinator.extensions

import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.DeltakerDetaljerResponse
import no.nav.amt.deltaker.bff.navtiltakskoordinator.model.TiltakskoordinatorsDeltaker
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelse
import no.nav.amt.deltaker.bff.veileder.api.response.ForslagResponse
import no.nav.amt.internapi.deltaker.response.VurderingResponse
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType

fun TiltakskoordinatorsDeltaker.toResponse(
    harTilgangTilBruker: Boolean,
    ulesteHendelser: List<UlestHendelse>,
): DeltakerDetaljerResponse {
    val (fornavn, mellomnavn, etternavn) = navBruker.getVisningsnavn(harTilgangTilBruker)
    val personIdent = if (harTilgangTilBruker) navBruker.personident else null
    val aktiveForslag = forslag
        .filter { forslag -> forslag.status == Forslag.Status.VenterPaSvar }
        .map {
            ForslagResponse.fromForslag(
                forslag = it,
                arrangornavn = deltakerliste.arrangor.getArrangorNavn(),
                ansatte = emptyMap(), // trenger ikke ansatte eller enheter
                enheter = emptyMap(),
            )
        }

    return DeltakerDetaljerResponse(
        id = id,
        fornavn = fornavn,
        mellomnavn = mellomnavn,
        etternavn = etternavn,
        fodselsnummer = personIdent,
        status = status.toResponse(),
        startdato = startdato,
        sluttdato = sluttdato,
        navEnhet = navEnhet,
        navVeileder = navVeileder,
        beskyttelsesmarkering = beskyttelsesmarkering,
        vurdering = vurdering?.let {
            VurderingResponse(
                type = vurdering.vurderingstype,
                begrunnelse = vurdering.begrunnelse,
            )
        },
        innsatsgruppe = innsatsgruppe,
        tiltakskode = deltakerliste.tiltak.tiltakskode,
        oppstartstype = deltakerliste.oppstart,
        pameldingstype = deltakerliste.pameldingstype,
        tilgangTilBruker = harTilgangTilBruker,
        aktiveForslag = aktiveForslag,
        ulesteHendelser = ulesteHendelser,
        deltakelsesinnhold = getDeltakelsesinnholdAnnet(harTilgangTilBruker, deltakerliste.pameldingstype, deltakelsesinnhold),
    )
}

fun getDeltakelsesinnholdAnnet(
    harTilgangTilBruker: Boolean,
    pameldingstype: GjennomforingPameldingType?,
    deltakelsesinnhold: Deltakelsesinnhold?,
): String? {
    if (!harTilgangTilBruker || pameldingstype == null || pameldingstype == GjennomforingPameldingType.DIREKTE_VEDTAK) {
        return null
    }
    return deltakelsesinnhold?.getAnnetFritekstBeskrivelse()
}
