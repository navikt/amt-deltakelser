package no.nav.tiltaksarrangor.repositories.model

import no.nav.tiltaksarrangor.consumer.model.EndringsmeldingType
import no.nav.tiltaksarrangor.consumer.model.Innhold
import no.nav.tiltaksarrangor.consumer.model.toEndringsmeldingInnhold
import no.nav.tiltaksarrangor.model.Endringsmelding
import java.time.LocalDateTime
import java.util.UUID

data class EndringsmeldingDbo(
	val id: UUID,
	val deltakerId: UUID,
	val type: EndringsmeldingType,
	val innhold: Innhold?,
	val status: Endringsmelding.Status,
	val sendt: LocalDateTime,
) {
	fun toEndringsmelding(): Endringsmelding = Endringsmelding(
		id = id,
		innhold = innhold?.toEndringsmeldingInnhold(),
		type = Endringsmelding.Type.valueOf(type.name),
		status = status,
		sendt = sendt.toLocalDate(),
	)

	fun erAktiv(): Boolean = status == Endringsmelding.Status.AKTIV
}
