package no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response

import no.nav.amt.deltaker.bff.deltaker.model.DeltakerModel
import no.nav.amt.deltaker.bff.navtiltakskoordinator.extensions.getDeltakelsesinnholdAnnet
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulesthendelse.model.UlestHendelse
import no.nav.amt.deltaker.bff.veileder.api.response.ForslagResponse
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.NavVeilederResponse
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType

class ResponseBuilder {
    companion object {
        fun buildDeltakerDetaljerResponse(
            deltaker: DeltakerModel,
            tilgangTilBruker: Boolean,
            ulesteHendelser: List<UlestHendelse>,
        ) = with(deltaker) {
            val (fornavn, mellomnavn, etternavn) = deltaker.navBruker.getVisningsnavn(tilgangTilBruker)
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
                fornavn = fornavn,
                mellomnavn = mellomnavn,
                etternavn = etternavn,
                fodselsnummer = if (tilgangTilBruker) navBruker.personident else null,
                status = DeltakerStatusResponse(
                    type = status.type,
                    aarsak = status.aarsak?.let { DeltakerStatusAarsakResponse(it.type, it.beskrivelse) },
                ),
                startdato = startdato,
                sluttdato = sluttdato,
                navEnhet = navBruker.navEnhet,
                // Beholder tom instansiering fordi det er dette som skjedde i den gamle koden som brukes av frontend
                navVeileder = navBruker.navVeileder ?: NavVeilederResponse("", null, null),
                vurdering = sisteVurdering,
                beskyttelsesmarkering = navBruker.beskyttelsesmarkeringer,
                innsatsgruppe = navBruker.innsatsgruppe,
                tiltakskode = deltaker.gjennomforing.tiltak.tiltakskode,
                tilgangTilBruker = tilgangTilBruker,
                aktiveForslag = aktiveForslag,
                ulesteHendelser = ulesteHendelser,
                oppstartstype = gjennomforing.oppstart,
                // Hvorfor er denne optional?
                pameldingstype = gjennomforing.pameldingstype ?: GjennomforingPameldingType.TRENGER_GODKJENNING,
                deltakelsesinnhold = getDeltakelsesinnholdAnnet(tilgangTilBruker, gjennomforing.pameldingstype, deltakelsesinnhold),
            )
        }
    }
}
