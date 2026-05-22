package no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response

import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.UlestHendelseRepository
import no.nav.amt.internapi.deltaker.response.TiltakskoordinatorDeltakerResponse

class ResponseBuilder(
    private val ulestHendelseRepository: UlestHendelseRepository,
) {
    /**
     * Bygger liste-respons fra den spissede [TiltakskoordinatorDeltakerResponse] fra amt-deltaker.
     * Henter ulestehendelser i bulk for å unngå N+1-spørringer.
     */
    fun toDeltakerResponses(
        deltakere: List<TiltakskoordinatorDeltakerResponse>,
        kanSeInnbyggersNavn: (TiltakskoordinatorDeltakerResponse) -> Boolean,
    ): List<DeltakerResponse> {
        val ulesteHendelserPerDeltaker = ulestHendelseRepository
            .getForDeltakere(deltakere.map { it.id }.toSet())

        return deltakere.map { deltaker ->
            val ulestFlags = ulesteHendelserPerDeltaker[deltaker.id]
            buildDeltakerResponse(
                deltaker = deltaker,
                kanSeInnbyggersNavn = kanSeInnbyggersNavn(deltaker),
                erNyDeltaker = ulestFlags?.erNyDeltaker == true,
                harOppdateringFraNav = ulestFlags?.harOppdateringFraNav == true,
            )
        }
    }

    private fun buildDeltakerResponse(
        deltaker: TiltakskoordinatorDeltakerResponse,
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
            feilkode = null,
            ikkeDigitalOgManglerAdresse = navBruker.ikkeDigitalOgManglerAdresse,
            harAktiveForslag = harAktivtForslag,
            erNyDeltaker = erNyDeltaker,
            harOppdateringFraNav = harOppdateringFraNav,
            kanEndres = true,
            soktInnDato = soktInnDato,
            startdato = startdato,
            sluttdato = sluttdato,
        )
    }
}
