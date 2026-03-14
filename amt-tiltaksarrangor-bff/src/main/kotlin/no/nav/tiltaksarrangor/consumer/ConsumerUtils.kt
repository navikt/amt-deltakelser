package no.nav.tiltaksarrangor.consumer

import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.tiltaksarrangor.repositories.model.DeltakerlisteDbo
import no.nav.tiltaksarrangor.utils.objectMapper
import java.time.LocalDate
import java.util.UUID

object ConsumerUtils {
	private const val DELTAKERLISTE_KEY = "deltakerliste"
	private const val LISTE_GJENNOMFORINGSTYPE_KEY = "gjennomforingstype"

	const val GJENNOMFORINGSTYPE_KEY = "type"
	private const val FALLBACK_GJENNOMFORINGSTYPE = "UKJENT"

	fun getGjennomforingstypeFromJson(messageJson: String): String = objectMapper
		.readTree(messageJson)
		.get(GJENNOMFORINGSTYPE_KEY)
		?.asString()
		?: FALLBACK_GJENNOMFORINGSTYPE

	fun getGjennomforingstypeFromDeltakerJsonPayload(messageJson: String): String = objectMapper
		.readTree(messageJson)
		.get(DELTAKERLISTE_KEY)
		?.get(LISTE_GJENNOMFORINGSTYPE_KEY)
		?.asString()
		?: FALLBACK_GJENNOMFORINGSTYPE

	private fun mapTiltakstypeNavn(tiltakstypeNavn: String): String = if (tiltakstypeNavn == "Jobbklubb") {
		"Jobbsøkerkurs"
	} else {
		tiltakstypeNavn
	}

	fun GjennomforingV2KafkaPayload.Gruppe.toDeltakerlisteDbo(arrangorId: UUID, navnTiltakstype: String): DeltakerlisteDbo = DeltakerlisteDbo(
		id = id,
		navn = navn,
		gjennomforingstype = gjennomforingType,
		status = status,
		arrangorId = arrangorId,
		tiltaksnavn = mapTiltakstypeNavn(navnTiltakstype),
		tiltakskode = tiltakskode,
		startDato = startDato,
		sluttDato = sluttDato,
		erKurs = oppstart == Oppstartstype.FELLES,
		oppstartstype = oppstart,
		tilgjengeligForArrangorFraOgMedDato = tilgjengeligForArrangorFraOgMedDato,
		pameldingstype = pameldingType,
	)

	fun GjennomforingV2KafkaPayload.Gruppe.skalLagres(): Boolean = when (this.status) {
		GjennomforingStatusType.GJENNOMFORES -> true

		GjennomforingStatusType.AVSLUTTET ->
			sluttDato
				?.let { LocalDate.now().isBefore(it.plusDays(15)) }
				?: false

		else -> false
	}
}
