package no.nav.amt.deltaker.repository.dbo

import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.Kilde
import java.util.UUID

data class DeltakerKladdUpsertDbo(
    val id: UUID,
    val navBrukerId: UUID,
    val deltakerlisteId: UUID,
    val dagerPerUke: Float? = null,
    val deltakelsesprosent: Float? = null,
    val bakgrunnsinformasjon: String?,
    val deltakelsesinnhold: Deltakelsesinnhold?,
    val kilde: Kilde,
    val erManueltDeltMedArrangor: Boolean = false,
)
