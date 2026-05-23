package no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response

import no.nav.amt.internapi.tiltakskoordinator.HandlingFilterValg
import no.nav.amt.internapi.tiltakskoordinator.response.DeltakerOppdateringFeilkode
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.person.Beskyttelsesmarkering
import java.time.LocalDate
import java.util.UUID

data class DeltakerResponse(
    val id: UUID,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val status: DeltakerStatusResponse,
    val beskyttelsesmarkering: List<Beskyttelsesmarkering>,
    val vurdering: Vurderingstype?,
    val navEnhet: String?,
    val erManueltDeltMedArrangor: Boolean,
    val feilkode: DeltakerOppdateringFeilkode? = null,
    val ikkeDigitalOgManglerAdresse: Boolean,
    val harAktiveForslag: Boolean,
    val harOppdateringFraNav: Boolean,
    val erNyDeltaker: Boolean,
    val kanEndres: Boolean,
    val soktInnDato: LocalDate?,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
) {
    fun matchesHandlingFilter(handlingFilterValg: Set<HandlingFilterValg>): Boolean = if (handlingFilterValg.isEmpty()) {
        true
    } else {
        (HandlingFilterValg.NyeDeltakere in handlingFilterValg && erNyDeltaker) ||
            (HandlingFilterValg.OppdateringFraNav in handlingFilterValg && harOppdateringFraNav) ||
            (HandlingFilterValg.AktiveForslag in handlingFilterValg && harAktiveForslag)
    }
}
