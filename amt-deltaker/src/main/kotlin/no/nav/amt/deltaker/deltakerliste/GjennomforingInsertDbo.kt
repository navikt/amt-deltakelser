package no.nav.amt.deltaker.deltakerliste

import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import java.util.UUID

data class GjennomforingInsertDbo(
    val id: UUID,
    val type: GjennomforingType,
    val tiltakId: UUID,
    val navn: String,
    val status: GjennomforingStatusType?,
    val oppstart: Oppstartstype?,
    val apentForPamelding: Boolean,
    val pameldingstype: GjennomforingPameldingType?,
)
