package no.nav.tiltaksarrangor.repositories.model

import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.time.LocalDate
import java.util.UUID

data class DeltakerlisteDbo(
	val id: UUID,
	val navn: String,
	val status: GjennomforingStatusType,
	val arrangorId: UUID,
	val gjennomforingstype: GjennomforingType,
	val tiltaksnavn: String,
	val tiltakskode: Tiltakskode,
	val startDato: LocalDate?,
	val sluttDato: LocalDate?,
	val erKurs: Boolean, // Brukes ikke mer. Kan fjernes fra db
	val oppstartstype: Oppstartstype,
	val tilgjengeligForArrangorFraOgMedDato: LocalDate?,
	val pameldingstype: GjennomforingPameldingType?, // skal gjøres  non-nullable etter relast
) {
	fun erTilgjengeligForArrangor(): Boolean = if (startDato != null) {
		if (tilgjengeligForArrangorFraOgMedDato != null) {
			!tilgjengeligForArrangorFraOgMedDato.isAfter(LocalDate.now())
		} else {
			!startDato.isAfter(LocalDate.now().plusDays(14))
		}
	} else {
		false
	}

	fun skalViseAdresseForDeltaker(): Boolean = tiltakstyperMedAdresse.contains(tiltakskode)

	private val tiltakstyperMedAdresse: Set<Tiltakskode> =
		setOf(
			Tiltakskode.OPPFOLGING,
			Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
			Tiltakskode.AVKLARING,
			Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET,
			Tiltakskode.ARBEIDSRETTET_REHABILITERING,
		)
}
