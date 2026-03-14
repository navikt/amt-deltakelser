package no.nav.tiltaksarrangor.client.amtarrangor.dto

import no.nav.tiltaksarrangor.consumer.model.ArrangorDto
import no.nav.tiltaksarrangor.repositories.model.ArrangorDbo
import java.util.UUID

data class ArrangorMedOverordnetArrangor(
	val id: UUID,
	val navn: String,
	val organisasjonsnummer: String,
	val overordnetArrangor: ArrangorDto?,
)

fun ArrangorMedOverordnetArrangor.toArrangorDbo(): ArrangorDbo = ArrangorDbo(
	id = id,
	navn = navn,
	organisasjonsnummer = organisasjonsnummer,
	overordnetArrangorId = overordnetArrangor?.id,
)
