package no.nav.amt.internapi.tiltakskoordinator.response

import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDate
import java.util.UUID

/**
 * Spisset respons-type for tiltakskoordinator-lista (`POST /tiltakskoordinator/deltakere/{gjennomforingId}`).
 *
 * Kun data BFF faktisk bruker for å rendre liste-visningen er med — ingen forslag-JSONB,
 * ingen full vurdering, ingen vedtaksinformasjon, deltakelsesinnhold eller bakgrunnsinformasjon.
 */

data class TiltakskoordinatorDeltakerResponse(
    val id: UUID,
    val status: DeltakerStatus,
    val navBruker: TiltakskoordinatorNavBrukerResponse,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val soktInnDato: LocalDate?,
    val erManueltDeltMedArrangor: Boolean,
    val harAktivtForslag: Boolean,
    val sisteVurderingstype: Vurderingstype?,
)
