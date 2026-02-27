package no.nav.amt.deltaker.deltaker.kafka.dto

import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Kilde
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class DeltakerEksternV1Dto(
    val id: UUID,
    val gjennomforingId: UUID,
    val personIdent: String,
    val startDato: LocalDate?,
    val sluttDato: LocalDate?,
    val status: StatusDto,
    val registrertTidspunkt: LocalDateTime,
    val endretTidspunkt: LocalDateTime,
    val kilde: Kilde,
    val innhold: DeltakelsesinnholdDto?,
    val deltakelsesmengder: List<DeltakelsesmengdeDto>,
) {
    data class StatusDto(
        val type: DeltakerStatus.Type,
        val tekst: String,
        val aarsak: AarsakDto,
        val opprettetTidspunkt: LocalDateTime,
    )

    data class AarsakDto(
        val type: DeltakerStatus.Aarsak.Type?,
        val beskrivelse: String?,
    )

    data class DeltakelsesinnholdDto(
        val ledetekst: String?,
        val valgtInnhold: List<InnholdDto>,
    )

    data class InnholdDto(
        val tekst: String,
        val innholdskode: String,
    )

    data class DeltakelsesmengdeDto(
        val deltakelsesprosent: Float,
        val dagerPerUke: Float?,
        val gyldigFraDato: LocalDate,
        val opprettetTidspunkt: LocalDateTime,
    )
}
