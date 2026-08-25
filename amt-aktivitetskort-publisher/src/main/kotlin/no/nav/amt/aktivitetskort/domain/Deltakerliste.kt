package no.nav.amt.aktivitetskort.domain

import net.minidev.json.annotate.JsonIgnore
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import java.util.UUID

data class Deltakerliste(
    val id: UUID,
    val tiltak: Tiltak,
    val navn: String,
    val arrangorId: UUID,
    // følgende felter kan settes som non-nullable etter relast
    val gjennomforingstype: GjennomforingType?,
    val status: GjennomforingStatusType?,
    val oppstart: Oppstartstype?,
    val pameldingstype: GjennomforingPameldingType?,
) {
    // enkeltplass opplæring etter ny forskrift
    @get:JsonIgnore
    val nyForskriftOpplaring
        get() = gjennomforingstype == GjennomforingType.Enkeltplass &&
            !tiltak.tiltakskode.erArenaEnkeltplass()
}
