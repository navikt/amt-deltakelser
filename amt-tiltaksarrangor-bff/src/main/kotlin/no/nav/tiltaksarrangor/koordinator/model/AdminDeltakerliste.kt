package no.nav.tiltaksarrangor.koordinator.model

import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import java.time.LocalDate
import java.util.UUID

data class AdminDeltakerliste(
	val id: UUID,
	val navn: String,
	val tiltaksnavn: String,
	val arrangorNavn: String,
	val arrangorOrgnummer: String,
	val arrangorParentNavn: String,
	val startDato: LocalDate?,
	val sluttDato: LocalDate?,
	val lagtTil: Boolean,
	val oppstartstype: Oppstartstype,
)
