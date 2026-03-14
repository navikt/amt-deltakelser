package no.nav.tiltaksarrangor.consumer

import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.tiltaksarrangor.client.amtarrangor.dto.ArrangorMedOverordnetArrangor
import no.nav.tiltaksarrangor.consumer.model.TiltakstypePayload
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

object ConsumerTestUtils {
	// val test = lagArrangor("Test")

	val arrangorInTest =
		ArrangorMedOverordnetArrangor(
			id = UUID.randomUUID(),
			navn = "Arrangør AS",
			organisasjonsnummer = "987654321",
			overordnetArrangor = null,
		)

	val tiltakstypePayloadInTest = TiltakstypePayload(
		id = UUID.randomUUID(),
		navn = "Navn",
		tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING.name,
	)

	val deltakerlisteIdInTest: UUID = UUID.randomUUID()

	val gjennomforingPayloadInTest =
		GjennomforingV2KafkaPayload.Gruppe(
			id = deltakerlisteIdInTest,
			tiltakskode = Tiltakskode.valueOf(tiltakstypePayloadInTest.tiltakskode),
			navn = "Gjennomføring av tiltak",
			startDato = LocalDate.now().minusYears(2),
			sluttDato = null,
			status = GjennomforingStatusType.GJENNOMFORES,
			oppstart = Oppstartstype.LOPENDE,
			tilgjengeligForArrangorFraOgMedDato = LocalDate.now(),
			arrangor = GjennomforingV2KafkaPayload.Arrangor(arrangorInTest.organisasjonsnummer),
			oppdatertTidspunkt = OffsetDateTime.now(),
			opprettetTidspunkt = OffsetDateTime.now(),
			apentForPamelding = true,
			antallPlasser = 42,
			oppmoteSted = null,
			deltidsprosent = 100.0,
			pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
		)
}
