package no.nav.tiltaksarrangor.koordinator.model

import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.time.LocalDate
import java.util.UUID

data class Deltakerliste(
	val id: UUID,
	val navn: String,
	val tiltaksnavn: String,
	val arrangorNavn: String,
	val startDato: LocalDate?,
	val sluttDato: LocalDate?,
	val status: GjennomforingStatusType,
	val koordinatorer: List<Koordinator>,
	val deltakere: List<Deltaker>,
	val tiltakskode: Tiltakskode,
	val oppstartstype: Oppstartstype,
	val pameldingstype: GjennomforingPameldingType,
)
