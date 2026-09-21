package no.nav.amt.aktivitetskort.domain

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
)
