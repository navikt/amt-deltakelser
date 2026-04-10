package no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response

import no.nav.amt.deltaker.bff.deltaker.model.DeltakerModel
import no.nav.amt.deltaker.bff.navtiltakskoordinator.extensions.getDeltakelsesinnholdAnnet
import no.nav.amt.deltaker.bff.navtiltakskoordinator.model.NavVeileder
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulesthendelse.model.UlestHendelse
import no.nav.amt.deltaker.bff.veileder.api.response.ForslagResponse
import no.nav.amt.lib.models.arrangor.melding.Forslag

class ResponseBuilder {
    companion object {
        fun buildDeltakerDetaljerResponse(
            deltaker: DeltakerModel,
            tilgangTilBruker: Boolean,
            ulesteHendelser: List<UlestHendelse>,
        ) = with(deltaker) {
            val aktiveForslag = endringsforslagFraArrangor
                .filter { forslag -> forslag.status == Forslag.Status.VenterPaSvar }
                .map {
                    ForslagResponse.fromForslag(
                        forslag = it,
                        arrangornavn =
                            gjennomforing.arrangor?.navn
                                ?: throw IllegalStateException("Kan ikke ha forslag på en deltakelse uten arrangør"),
                        ansatte = emptyMap(), // trenger ikke ansatte eller enheter
                        enheter = emptyMap(),
                    )
                }

            DeltakerDetaljerResponse(
                id = id,
                fornavn = navBruker.fornavn,
                etternavn = navBruker.etternavn,
                fodselsnummer = navBruker.personident,
                status = DeltakerStatusResponse(
                    type = status.type,
                    aarsak = status.aarsak?.let { DeltakerStatusAarsakResponse(it.type, it.beskrivelse) },
                ),
                startdato = startdato,
                sluttdato = sluttdato,
                navEnhet = navBruker.navEnhet,
                // TODO: amt-deltaker må returnere mer info om veileder
                navVeileder = NavVeileder(navBruker.navVeileder, null, null),
                vurdering = sisteVurdering,
                beskyttelsesmarkering = navBruker.beskyttelsesmarkeringer,
                innsatsgruppe = navBruker.innsatsgruppe,
                tiltakskode = deltaker.gjennomforing.tiltak.tiltakskode,
                tilgangTilBruker = tilgangTilBruker,
                aktiveForslag = aktiveForslag,
                ulesteHendelser = ulesteHendelser,
                oppstartstype = gjennomforing.oppstart,
                pameldingstype = gjennomforing.pameldingstype!!, // Hvorfor er denne optional?
                deltakelsesinnhold = getDeltakelsesinnholdAnnet(tilgangTilBruker, gjennomforing.pameldingstype, deltakelsesinnhold),
            )
        }
    }
}
