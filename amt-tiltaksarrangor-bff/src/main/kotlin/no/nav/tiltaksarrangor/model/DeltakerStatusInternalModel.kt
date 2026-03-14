package no.nav.tiltaksarrangor.model

import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDateTime

// Denne brukes i domeneobjekt som også returneres av api og har en litt
// Annen navngivning enn DeltakerStatus
data class DeltakerStatusInternalModel(
	val type: DeltakerStatus.Type,
	val endretDato: LocalDateTime,
	val aarsak: DeltakerStatusAarsakJsonDboDto?,
)
