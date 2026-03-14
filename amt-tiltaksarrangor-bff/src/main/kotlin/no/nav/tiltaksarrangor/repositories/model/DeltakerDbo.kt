package no.nav.tiltaksarrangor.repositories.model

import no.nav.amt.lib.models.arrangor.melding.Vurdering
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.person.Oppfolgingsperiode
import no.nav.tiltaksarrangor.consumer.model.AdresseJsonDbo
import no.nav.tiltaksarrangor.model.DeltakerStatusAarsakJsonDboDto
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class DeltakerDbo(
	val id: UUID,
	val deltakerlisteId: UUID,
	val personident: String,
	val fornavn: String,
	val mellomnavn: String?,
	val etternavn: String,
	val telefonnummer: String?,
	val epost: String?,
	val erSkjermet: Boolean,
	val adresse: AdresseJsonDbo?,
	val status: DeltakerStatus.Type,
	val statusOpprettetDato: LocalDateTime,
	val statusGyldigFraDato: LocalDateTime,
	val statusAarsak: DeltakerStatusAarsakJsonDboDto?,
	val dagerPerUke: Float?,
	val prosentStilling: Double?,
	val startdato: LocalDate?,
	val sluttdato: LocalDate?,
	val innsoktDato: LocalDate,
	val bestillingstekst: String?,
	val navKontor: String?,
	val navVeilederId: UUID?,
	val navVeilederNavn: String?,
	val navVeilederEpost: String?,
	val navVeilederTelefon: String?,
	val skjultAvAnsattId: UUID?,
	val skjultDato: LocalDateTime?,
	val vurderingerFraArrangor: List<Vurdering>?,
	val adressebeskyttet: Boolean,
	val innhold: Deltakelsesinnhold?,
	val kilde: Kilde?,
	val historikk: List<DeltakerHistorikk>,
	val sistEndret: LocalDateTime,
	val forsteVedtakFattet: LocalDate?,
	val erManueltDeltMedArrangor: Boolean,
	val oppfolgingsperioder: List<Oppfolgingsperiode> = emptyList(),
) {
	fun erSkjult(): Boolean = skjultDato != null

	fun skalFjernesDato(): LocalDateTime? = if (status in STATUSER_SOM_KAN_SKJULES) {
		statusGyldigFraDato.plusDays(DAGER_AVSLUTTET_DELTAKER_VISES)
	} else {
		null
	}

	fun skalVises(): Boolean {
		if (sluttdato == null) {
			return true
		}

		val dato = LocalDate.now().minusDays(DAGER_AVSLUTTET_DELTAKER_VISES)
		return sluttdato.isAfter(dato)
	}

	val erUnderOppfolging: Boolean get() = oppfolgingsperioder.any { it.erAktiv() }
}

val STATUSER_SOM_KAN_SKJULES =
	listOf(
		DeltakerStatus.Type.IKKE_AKTUELL,
		DeltakerStatus.Type.HAR_SLUTTET,
		DeltakerStatus.Type.FULLFORT,
		DeltakerStatus.Type.AVBRUTT,
	)

const val DAGER_AVSLUTTET_DELTAKER_VISES = 40L
