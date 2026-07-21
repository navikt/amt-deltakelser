package no.nav.amt.aktivitetskort.domain

import java.time.ZonedDateTime
import java.util.UUID

data class Melding(
    val id: UUID,
    val deltakerId: UUID,
    val deltakerlisteId: UUID,
    val arrangorId: UUID,
    val aktivitetskort: Aktivitetskort,
    val oppfolgingperiode: UUID?,
    val createdAt: ZonedDateTime = ZonedDateTime.now(),
    val modifiedAt: ZonedDateTime = ZonedDateTime.now(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Melding

        if (deltakerId != other.deltakerId) return false
        if (deltakerlisteId != other.deltakerlisteId) return false
        if (arrangorId != other.arrangorId) return false
        return aktivitetskort == other.aktivitetskort
    }

    override fun hashCode(): Int {
        var result = deltakerId.hashCode()
        result = 31 * result + deltakerlisteId.hashCode()
        result = 31 * result + arrangorId.hashCode()
        result = 31 * result + aktivitetskort.hashCode()
        return result
    }
}
