package no.nav.amt.lib.models.deltaker

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class Innsok(
    val id: UUID,
    val deltakerId: UUID,
    val innsokt: LocalDateTime,
    val innsoktAv: UUID,
    val innsoktAvEnhet: UUID,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val dagerPerUkeVedInnsok: Int? = null,
    val deltakelsesinnholdVedInnsok: Deltakelsesinnhold?,
    val prisinformasjonVedInnsok: PrisinformasjonDto? = null,
    val opplaringKategoriseringVedInnsok: OpplaringKategoriseringValg?,
    val utkastDelt: LocalDateTime?,
    val utkastGodkjentAvNav: Boolean,
)
