package no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response

import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.UlestHendelseRepository
import no.nav.amt.internapi.tiltakskoordinator.response.DeltakerOppdateringFeilkode
import no.nav.amt.internapi.tiltakskoordinator.response.DeltakerOppdateringResponse
import no.nav.amt.internapi.tiltakskoordinator.response.TiltakskoordinatorDeltakerIListeResponse
import java.util.UUID

class ResponseBuilder(
    private val ulestHendelseRepository: UlestHendelseRepository,
) {
    fun toOppdatertDeltakerResponse(
        deltakere: List<DeltakerOppdateringResponse>,
        kanSeInnbyggersNavn: (TiltakskoordinatorDeltakerIListeResponse) -> Boolean,
    ) = toDeltakereResponse(
        deltakere = deltakere.map { it.deltaker },
        feilkoder = deltakere.associateBy { it.deltaker.id }.mapValues { it.value.feilkode },
        kanSeInnbyggersNavn = kanSeInnbyggersNavn,
    )

    /**
     * Bygger liste-respons fra den spissede [TiltakskoordinatorDeltakerIListeResponse] fra amt-deltaker.
     * Henter ulestehendelser i bulk for å unngå N+1-spørringer.
     * Feilkode vil i tilfelle ren uthenting alltid være null men settes
     * i tilfelle en oppdatering av deltaker har feilet.
     */
    fun toDeltakereResponse(
        deltakere: List<TiltakskoordinatorDeltakerIListeResponse>,
        feilkoder: Map<UUID, DeltakerOppdateringFeilkode?> = emptyMap(),
        kanSeInnbyggersNavn: (TiltakskoordinatorDeltakerIListeResponse) -> Boolean,
    ): List<DeltakerResponse> {
        val ulesteHendelserPerDeltaker = ulestHendelseRepository
            .getForDeltakere(deltakere.map { it.id }.toSet())

        return deltakere.map { deltaker ->
            val ulestFlags = ulesteHendelserPerDeltaker[deltaker.id]
            buildDeltakerResponse(
                deltaker = deltaker,
                feilkode = feilkoder[deltaker.id],
                kanSeInnbyggersNavn = kanSeInnbyggersNavn(deltaker),
                erNyDeltaker = ulestFlags?.erNyDeltaker == true,
                harOppdateringFraNav = ulestFlags?.harOppdateringFraNav == true,
            )
        }
    }

    fun buildDeltakerResponse(
        deltaker: TiltakskoordinatorDeltakerIListeResponse,
        feilkode: DeltakerOppdateringFeilkode?,
        kanSeInnbyggersNavn: Boolean,
        erNyDeltaker: Boolean,
        harOppdateringFraNav: Boolean,
    ): DeltakerResponse = with(deltaker) {
        val (fornavn, mellomnavn, etternavn) = navBruker.getVisningsnavn(kanSeInnbyggersNavn)

        DeltakerResponse(
            id = id,
            fornavn = fornavn,
            mellomnavn = mellomnavn,
            etternavn = etternavn,
            status = DeltakerStatusResponse(
                type = status.type,
                aarsak = status.aarsak?.let {
                    DeltakerStatusAarsakResponse(it.type, it.beskrivelse)
                },
            ),
            vurdering = sisteVurderingstype,
            beskyttelsesmarkering = navBruker.beskyttelsesmarkeringer,
            navEnhet = navBruker.navEnhet,
            erManueltDeltMedArrangor = erManueltDeltMedArrangor,
            feilkode = feilkode,
            ikkeDigitalOgManglerAdresse = navBruker.ikkeDigitalOgManglerAdresse,
            harAktiveForslag = harAktivtForslag,
            erNyDeltaker = erNyDeltaker,
            harOppdateringFraNav = harOppdateringFraNav,
            kanEndres = kanEndres,
            soktInnDato = soktInnDato,
            startdato = startdato,
            sluttdato = sluttdato,
        )
    }
}
