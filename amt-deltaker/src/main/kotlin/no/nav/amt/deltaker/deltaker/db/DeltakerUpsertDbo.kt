package no.nav.amt.deltaker.deltaker.db

import no.nav.amt.deltaker.deltaker.model.Vedtaksinformasjon
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Kilde
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class DeltakerUpsertDbo(
    val id: UUID,
    val navBrukerId: UUID,
    val deltakerlisteId: UUID,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val dagerPerUke: Float?,
    val deltakelsesprosent: Float?,
    val bakgrunnsinformasjon: String?,
    val deltakelsesinnhold: Deltakelsesinnhold?,
    val status: DeltakerStatus,
    val vedtaksinformasjon: Vedtaksinformasjon?,
    val sistEndret: LocalDateTime,
    val kilde: Kilde,
    val erManueltDeltMedArrangor: Boolean,
)
