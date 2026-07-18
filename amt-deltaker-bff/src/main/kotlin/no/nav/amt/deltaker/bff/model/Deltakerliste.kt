package no.nav.amt.deltaker.bff.model

import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import java.time.LocalDate
import java.util.UUID

data class Deltakerliste(
    val id: UUID,
    val tiltak: Tiltakstype,
    val navn: String,
    val status: GjennomforingStatusType,
    val startDato: LocalDate?,
    val sluttDato: LocalDate? = null,
    val oppstart: Oppstartstype,
    val arrangor: Arrangor,
    val apentForPamelding: Boolean,
    val antallPlasser: Int?,
    val oppmoteSted: String?,
    val pameldingstype: GjennomforingPameldingType,
) {
    data class Arrangor(
        val arrangor: no.nav.amt.lib.models.deltaker.Arrangor,
        val overordnetArrangorNavn: String?,
    )
}
