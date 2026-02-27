package no.nav.amt.deltaker.external.data

import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class DeltakerResponse(
    val id: UUID,
    val gjennomforing: GjennomforingResponse,
    val startDato: LocalDate?,
    val sluttDato: LocalDate?,
    val status: DeltakerStatus.Type,
    val dagerPerUke: Float?,
    val prosentStilling: Float?,
    val registrertDato: LocalDateTime,
)
