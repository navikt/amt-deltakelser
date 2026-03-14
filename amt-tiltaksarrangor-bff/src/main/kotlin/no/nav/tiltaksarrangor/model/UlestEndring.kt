package no.nav.tiltaksarrangor.model

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Forslag.Status
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.tiltaksarrangor.model.Oppdatering.DeltMedArrangor
import no.nav.tiltaksarrangor.model.Oppdatering.TildeltPlass
import java.time.LocalDate
import java.util.UUID

data class UlestEndring(
	val id: UUID,
	val deltakerId: UUID,
	val oppdatering: Oppdatering,
	val oppdatert: LocalDate,
) {
	fun erSvarFraNav(): Boolean = when (oppdatering) {
		is Oppdatering.DeltakelsesEndring -> oppdatering.endring.forslag != null
		is Oppdatering.AvvistForslag -> true
		is Oppdatering.NavBrukerEndring,
		is Oppdatering.NavEndring,
		is Oppdatering.NyDeltaker,
		is DeltMedArrangor,
		is TildeltPlass,
		is Oppdatering.Avslag,
		-> false
	}

	fun erOppdateringFraNav(): Boolean = !erSvarFraNav() && !erNyDeltaker()

	fun erNyDeltaker(): Boolean = when (oppdatering) {
		is Oppdatering.TildeltPlass -> oppdatering.erNyDeltaker
		is Oppdatering.DeltMedArrangor,
		is Oppdatering.NyDeltaker,
		-> true

		else -> false
	}

	fun hentNavAnsattId(): UUID? = when (oppdatering) {
		is Oppdatering.DeltakelsesEndring -> oppdatering.endring.endretAv
		is Oppdatering.AvvistForslag -> oppdatering.forslag.getNavAnsattForEndring().id
		is Oppdatering.Avslag,
		is Oppdatering.DeltMedArrangor,
		is Oppdatering.TildeltPlass,
		is Oppdatering.NavBrukerEndring,
		is Oppdatering.NavEndring,
		is Oppdatering.NyDeltaker,
		-> null
	}

	fun navEnheter(): UUID? = when (oppdatering) {
		is Oppdatering.DeltakelsesEndring -> oppdatering.endring.endretAvEnhet
		is Oppdatering.AvvistForslag -> oppdatering.forslag.getNavAnsattForEndring().enhetId
		is Oppdatering.NavBrukerEndring,
		is Oppdatering.NavEndring,
		is Oppdatering.NyDeltaker,
		is Oppdatering.DeltMedArrangor,
		is Oppdatering.TildeltPlass,
		is Oppdatering.Avslag,
		-> null
	}
}

private fun Forslag.getNavAnsattForEndring(): Forslag.NavAnsatt = when (val status = this.status) {
	is Status.Avvist -> status.avvistAv
	is Status.Godkjent,
	is Status.Erstattet,
	is Status.Tilbakekalt,
	Status.VenterPaSvar,
	-> throw IllegalStateException("Forslaget har status $status som ikke skal brukes i uleste endringer")
}

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface Oppdatering {
	data class DeltakelsesEndring(
		val endring: DeltakerEndring,
	) : Oppdatering

	data class AvvistForslag(
		val forslag: Forslag,
	) : Oppdatering

	data class NavBrukerEndring(
		val telefonnummer: String?,
		val epost: String?,
	) : Oppdatering

	data class NavEndring(
		val nyNavVeileder: Boolean,
		val navVeilederNavn: String?,
		val navVeilederEpost: String?,
		val navVeilederTelefonnummer: String?,
		val navEnhet: String?,
	) : Oppdatering

	data class NyDeltaker(
		val opprettetAvNavn: String?,
		val opprettetAvEnhet: String?,
		val opprettet: LocalDate,
	) : Oppdatering

	data class DeltMedArrangor(
		val deltAvNavn: String?,
		val deltAvEnhet: String?,
		val delt: LocalDate,
	) : Oppdatering

	data class TildeltPlass(
		val tildeltPlassAvNavn: String?,
		val tildeltPlassAvEnhet: String?,
		val tildeltPlass: LocalDate,
		val erNyDeltaker: Boolean,
	) : Oppdatering

	data class Avslag(
		val endretAv: String?,
		val endretAvEnhet: String?,
		val aarsak: DeltakerStatusAarsakJsonDboDto,
		val begrunnelse: String?,
	) : Oppdatering

	val id
		get() = when (this) {
			is DeltakelsesEndring -> endring.id
			is AvvistForslag -> forslag.id
			is NavBrukerEndring,
			is NavEndring,
			is NyDeltaker,
			is DeltMedArrangor,
			is TildeltPlass,
			is Avslag,
			-> UUID.randomUUID()
		}
}
