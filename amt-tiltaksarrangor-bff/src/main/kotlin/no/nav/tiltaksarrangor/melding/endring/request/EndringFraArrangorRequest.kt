package no.nav.tiltaksarrangor.melding.endring.request

import java.time.LocalDate

sealed interface EndringFraArrangorRequest

data class LeggTilOppstartsdatoRequest(
	val startdato: LocalDate,
	val sluttdato: LocalDate?,
) : EndringFraArrangorRequest
