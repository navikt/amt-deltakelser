package no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response

import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.deltaker.bff.navtiltakskoordinator.model.Tiltakskoordinator
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelse
import no.nav.amt.deltaker.bff.veileder.api.response.ForslagResponse
import no.nav.amt.internapi.deltaker.response.GjennomforingResponse
import no.nav.amt.internapi.deltaker.response.NavVeilederResponse
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType

object ResponseMapper {
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
                    arrangornavn = gjennomforing.arrangor?.navn
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
            navVeileder = navBruker.navVeileder ?: NavVeilederResponse(
                navn = null,
                telefonnummer = null,
                epost = null,
            ),
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
            deltakelsesinnhold = getDeltakelsesinnholdAnnet(
                harTilgangTilBruker = tilgangTilBruker,
                pameldingstype = gjennomforing.pameldingstype,
                deltakelsesinnhold = deltakelsesinnhold,
            ),
        )
    }

    fun buildGjennomforing(
        gjennomforingResponse: GjennomforingResponse,
        koordinatortilganger: List<Tiltakskoordinator>,
    ) = with(gjennomforingResponse) {
        DeltakerlisteResponse(
            id = id,
            navn = navn,
            tiltakskode = tiltakstype.tiltakskode,
            startdato = startDato,
            sluttdato = sluttDato,
            oppstartstype = oppstart,
            apentForPamelding = apentForPamelding,
            antallPlasser = antallPlasser,
            pameldingstype = pameldingstype ?: GjennomforingPameldingType.TRENGER_GODKJENNING,
            koordinatorer = koordinatortilganger,
            erEnkeltplass = type == GjennomforingType.Enkeltplass,
        )
    }

    internal fun getDeltakelsesinnholdAnnet(
        harTilgangTilBruker: Boolean,
        pameldingstype: GjennomforingPameldingType?,
        deltakelsesinnhold: Deltakelsesinnhold?,
    ): String? = if (!harTilgangTilBruker || pameldingstype == null || pameldingstype == GjennomforingPameldingType.DIREKTE_VEDTAK) {
        null
    } else {
        deltakelsesinnhold?.getAnnetFritekstBeskrivelse()
    }
}
