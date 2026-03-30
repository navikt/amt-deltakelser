package no.nav.amt.deltaker.deltaker.db

import no.nav.amt.deltaker.deltaker.model.Vedtaksinformasjon
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.Kilde
import java.time.LocalDate
import java.util.UUID

data class DeltakerUpsertDbo(
    val id: UUID,
    val navBrukerId: UUID,
    val deltakerlisteId: UUID,
    val startdato: LocalDate? = null,
    val sluttdato: LocalDate? = null,
    val dagerPerUke: Float? = null,
    val deltakelsesprosent: Float? = null,
    val bakgrunnsinformasjon: String?,
    val deltakelsesinnhold: Deltakelsesinnhold?,
    val vedtaksinformasjon: Vedtaksinformasjon? = null,
    val kilde: Kilde,
    val erManueltDeltMedArrangor: Boolean = false,
)
