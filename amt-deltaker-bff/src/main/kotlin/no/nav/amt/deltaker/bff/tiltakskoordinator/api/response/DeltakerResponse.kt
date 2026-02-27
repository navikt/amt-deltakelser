package no.nav.amt.deltaker.bff.tiltakskoordinator.api.response

import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.internalapis.tiltakskoordinator.response.DeltakerOppdateringFeilkode
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
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
)
