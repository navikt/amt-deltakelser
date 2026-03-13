package no.nav.amt.lib.models.deltaker.internalapis.deltaker.response

import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import java.time.LocalDate
import java.util.UUID

data class GjennomforingResponse(
    val id: UUID,
    val tiltakstype: Tiltakstype,
    val navn: String,
    val status: GjennomforingStatusType?,
    val startDato: LocalDate?,
    val sluttDato: LocalDate?,
    val oppstart: Oppstartstype?,
    val apentForPamelding: Boolean,
    val oppmoteSted: String?,
    val arrangor: ArrangorResponse?,
    val pameldingstype: GjennomforingPameldingType?,
)
