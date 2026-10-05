package no.nav.tiltaksarrangor.consumer

import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.kafka.AmtGjennomforingPayload
import no.nav.tiltaksarrangor.client.amtarrangor.dto.ArrangorMedOverordnetArrangor
import no.nav.tiltaksarrangor.consumer.model.TiltakstypePayload
import java.time.LocalDate
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
        AmtGjennomforingPayload(
            id = deltakerlisteIdInTest,
            type = GjennomforingType.Gruppe,
            tiltak = AmtGjennomforingPayload.TiltakPayload(
                id = UUID.randomUUID(),
                navn = tiltakstypePayloadInTest.navn,
                tiltakskode = Tiltakskode.valueOf(tiltakstypePayloadInTest.tiltakskode),
                innhold = null,
            ),
            arrangor = AmtGjennomforingPayload.Arrangor(arrangorInTest.organisasjonsnummer),
            status = GjennomforingStatusType.GJENNOMFORES,
            oppstart = Oppstartstype.LOPENDE,
            pameldingstype = GjennomforingPameldingType.DIREKTE_VEDTAK,
            navn = "Gjennomføring av tiltak",
            lopenummer = "2026-001",
            startDato = LocalDate.now().minusYears(2),
            sluttDato = null,
            tilgjengeligForArrangorFraOgMedDato = LocalDate.now(),
            apentForPamelding = true,
            antallPlasser = 42,
            oppmoteSted = null,
        )
}
