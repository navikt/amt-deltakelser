package no.nav.amt.lib.models.deltaker

import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltak
import java.time.LocalDate
import java.util.UUID

data class Deltakerliste(
    val id: UUID,
    val navn: String,
    val gjennomforingstype: GjennomforingType = GjennomforingType.Gruppe,
    val tiltak: Tiltak,
    val startdato: LocalDate? = null,
    val sluttdato: LocalDate? = null,
    val oppstartstype: Oppstartstype? = null,
)
