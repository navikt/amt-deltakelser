package no.nav.amt.internapi.deltaker.request

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype

/*
    Endringsrequest er en dto for å kommunisere alle endringer som kan gjøres på en deltaker
    fra amt-deltaker-bff til amt-deltaker
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface EndringRequest {
    val endretAv: String
    val endretAvEnhet: String

    /**
     * Konverterer requesten til en [DeltakerEndring.Endring]. De fleste requests kan konverteres
     * uten ekstra kontekst — overstyr denne.
     */
    fun toEndring(): DeltakerEndring.Endring = error(
        "${this::class.simpleName} må kalles via toEndring(deltaker)",
    )

    /**
     * Overload for requests som trenger data fra deltakerens tiltakstype (f.eks.
     * [EndretInnholdRequest] som henter `ledetekst` fra tiltakstypens registreringsinnhold).
     * Default delegerer til den parameterløse varianten.
     */
    fun toEndring(tiltak: Tiltakstype): DeltakerEndring.Endring = toEndring()

    fun kanIverksettesUtenAktivOppfolging() = when (this) {
        is BakgrunnsinformasjonRequest,
        is DeltakelsesmengdeRequest,
        is EndretInnholdRequest,
        is StartdatoRequest,
        is ForlengDeltakelseRequest,
        is ReaktiverDeltakelseRequest,
        is FjernOppstartsdatoRequest,
        -> false

        is AvsluttDeltakelseRequest,
        is AvbrytDeltakelseRequest,
        is EndreAvslutningRequest,
        is SluttarsakRequest,
        is SluttdatoRequest,
        is IkkeAktuellRequest,
        -> true
    }
}
