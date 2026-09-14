package no.nav.amt.lib.models.kafka

import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import java.time.LocalDate
import java.util.UUID

data class DeltakerlistePayload(
    val id: UUID,
    val navn: String,
    val gjennomforingstype: GjennomforingType = GjennomforingType.Gruppe,
    val tiltak: TiltakPayload,
    val startdato: LocalDate? = null,
    val sluttdato: LocalDate? = null,
    val oppstartstype: Oppstartstype? = null,
) {
    val erEnkeltplass = gjennomforingstype == GjennomforingType.Enkeltplass
}
