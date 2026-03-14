package no.nav.tiltaksarrangor.veileder.model

import no.nav.tiltaksarrangor.model.AktivEndring
import no.nav.tiltaksarrangor.model.DeltakerStatusInternalModel
import no.nav.tiltaksarrangor.model.Endringsmelding
import no.nav.tiltaksarrangor.model.Veiledertype
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class Deltaker(
	val id: UUID,
	val fornavn: String,
	val mellomnavn: String?,
	val etternavn: String,
	val fodselsnummer: String,
	val startDato: LocalDate?,
	val sluttDato: LocalDate?,
	val status: DeltakerStatusInternalModel,
	val deltakerliste: Deltakerliste,
	val veiledertype: Veiledertype,
	val aktiveEndringsmeldinger: List<Endringsmelding>,
	val aktivEndring: AktivEndring?,
	val sistEndret: LocalDateTime,
	val adressebeskyttet: Boolean,
	val svarFraNav: Boolean,
	val oppdateringFraNav: Boolean,
	val nyDeltaker: Boolean,
	val erUnderOppfolging: Boolean,
) {
	data class Deltakerliste(
		val id: UUID,
		val type: String,
		val navn: String,
	)
}
