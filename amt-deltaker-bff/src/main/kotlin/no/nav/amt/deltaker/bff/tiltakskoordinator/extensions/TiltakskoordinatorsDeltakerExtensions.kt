package no.nav.amt.deltaker.bff.tiltakskoordinator.extensions

import no.nav.amt.deltaker.bff.deltaker.api.model.getArrangorNavn
import no.nav.amt.deltaker.bff.deltaker.api.model.toResponse
import no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.annetInnholdselement
import no.nav.amt.deltaker.bff.tiltakskoordinator.api.response.DeltakerDetaljerResponse
import no.nav.amt.deltaker.bff.tiltakskoordinator.api.response.VurderingResponse
import no.nav.amt.deltaker.bff.tiltakskoordinator.model.TiltakskoordinatorsDeltaker
import no.nav.amt.deltaker.bff.tiltakskoordinator.ulesthendelse.model.UlestHendelse
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType

fun TiltakskoordinatorsDeltaker.toResponse(harTilgangTilBruker: Boolean, ulesteHendelser: List<UlestHendelse>): DeltakerDetaljerResponse {
    val (fornavn, mellomnavn, etternavn) = navBruker.getVisningsnavn(harTilgangTilBruker)
    val personIdent = if (harTilgangTilBruker) navBruker.personident else null
    val aktiveForslag = forslag
        .filter { forslag ->
            forslag.status == Forslag.Status.VenterPaSvar
        }.map { forslag -> forslag.toResponse(arrangornavn = deltakerliste.arrangor.getArrangorNavn()) }

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
        pameldingstype = deltakerliste.pameldingstype ?: GjennomforingPameldingType.TRENGER_GODKJENNING,
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

    val beskrivelseAnnet = deltakelsesinnhold
        ?.innhold
        ?.find { it.innholdskode == annetInnholdselement.innholdskode && it.valgt }
        ?.beskrivelse

    if (beskrivelseAnnet.isNullOrBlank()) {
        return null
    }
    return beskrivelseAnnet
}
