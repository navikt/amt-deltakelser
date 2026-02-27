package no.nav.amt.deltaker.external.data

import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDate
import java.util.UUID

data class DeltakerKort(
    val deltakerId: UUID,
    val deltakerlisteId: UUID,
    val tittel: String,
    val tiltakstype: DeltakelserResponse.Tiltakstype,
    val status: Status,
    val innsoktDato: LocalDate?,
    val sistEndretDato: LocalDate?,
    val periode: Periode?,
) {
    data class Status(
        val type: DeltakerStatus.Type,
        val visningstekst: String,
        val aarsak: String?,
    )
}
