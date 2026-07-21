package no.nav.amt.aktivitetskort.domain

import no.nav.amt.lib.models.deltaker.Kilde
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Objects
import java.util.UUID

data class Deltaker(
    val id: UUID,
    val personident: String,
    val deltakerlisteId: UUID,
    val status: DeltakerStatusModel,
    val dagerPerUke: Float?,
    val prosentStilling: Double?,
    val oppstartsdato: LocalDate?,
    val sluttdato: LocalDate?,
    val deltarPaKurs: Boolean,
    val kilde: Kilde?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Deltaker) return false

        return id == other.id &&
            personident == other.personident &&
            deltakerlisteId == other.deltakerlisteId &&
            status == other.status &&
            isEqual(dagerPerUke, other.dagerPerUke) &&
            isEqual(prosentStilling, other.prosentStilling) &&
            oppstartsdato == other.oppstartsdato &&
            sluttdato == other.sluttdato &&
            deltarPaKurs == other.deltarPaKurs &&
            kilde == other.kilde
    }

    private fun isEqual(
        dagerPerUke: Float?,
        otherDagerPerUke: Float?,
    ): Boolean = (dagerPerUke == otherDagerPerUke) ||
        (dagerPerUke == null && otherDagerPerUke == 0.0F) ||
        (dagerPerUke == 0.0F && otherDagerPerUke == null)

    private fun isEqual(
        prosentStilling: Double?,
        otherProsentStilling: Double?,
    ): Boolean = (prosentStilling == otherProsentStilling) ||
        (prosentStilling == null && otherProsentStilling == 0.0) ||
        (prosentStilling == 0.0 && otherProsentStilling == null)

    override fun hashCode(): Int = Objects.hash(
        id,
        personident,
        deltakerlisteId,
        status,
        dagerPerUke,
        prosentStilling,
        oppstartsdato,
        sluttdato,
        deltarPaKurs,
        kilde,
    )
}

data class DeltakerStatusModel(
    val type: no.nav.amt.lib.models.deltaker.DeltakerStatus.Type,
    val aarsak: no.nav.amt.lib.models.deltaker.DeltakerStatus.Aarsak.Type?,
    val gyldigFra: LocalDateTime? = null,
)

val IKKE_AVTALT_MED_NAV_STATUSER = listOf(
    no.nav.amt.lib.models.deltaker.DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
    no.nav.amt.lib.models.deltaker.DeltakerStatus.Type.AVBRUTT_UTKAST,
)
