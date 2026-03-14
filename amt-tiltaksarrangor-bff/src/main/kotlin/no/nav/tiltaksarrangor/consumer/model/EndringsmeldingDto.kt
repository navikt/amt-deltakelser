package no.nav.tiltaksarrangor.consumer.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.tiltaksarrangor.model.Endringsmelding
import no.nav.tiltaksarrangor.repositories.model.EndringsmeldingDbo
import java.time.LocalDateTime
import java.util.UUID

data class EndringsmeldingDto(
	val id: UUID,
	val deltakerId: UUID,
	val utfortAvNavAnsattId: UUID?,
	val opprettetAvArrangorAnsattId: UUID,
	val utfortTidspunkt: LocalDateTime?,
	val status: Endringsmelding.Status,
	val type: EndringsmeldingType,
	@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type")
	@JsonSubTypes(
		JsonSubTypes.Type(value = Innhold.LeggTilOppstartsdatoInnhold::class, name = "LEGG_TIL_OPPSTARTSDATO"),
		JsonSubTypes.Type(value = Innhold.EndreOppstartsdatoInnhold::class, name = "ENDRE_OPPSTARTSDATO"),
		JsonSubTypes.Type(value = Innhold.ForlengDeltakelseInnhold::class, name = "FORLENG_DELTAKELSE"),
		JsonSubTypes.Type(value = Innhold.AvsluttDeltakelseInnhold::class, name = "AVSLUTT_DELTAKELSE"),
		JsonSubTypes.Type(value = Innhold.DeltakerIkkeAktuellInnhold::class, name = "DELTAKER_IKKE_AKTUELL"),
		JsonSubTypes.Type(value = Innhold.EndreDeltakelseProsentInnhold::class, name = "ENDRE_DELTAKELSE_PROSENT"),
		JsonSubTypes.Type(value = Innhold.EndreSluttdatoInnhold::class, name = "ENDRE_SLUTTDATO"),
		JsonSubTypes.Type(value = Innhold.EndreSluttaarsakInnhold::class, name = "ENDRE_SLUTTAARSAK"),
	)
	val innhold: Innhold?,
	val createdAt: LocalDateTime,
)

fun EndringsmeldingDto.toEndringsmeldingDbo(): EndringsmeldingDbo = EndringsmeldingDbo(
	id = id,
	deltakerId = deltakerId,
	type = type,
	innhold = innhold,
	status = status,
	sendt = createdAt,
)
