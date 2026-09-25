package no.nav.amt.deltaker.bff.model

import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import java.time.LocalDate
import java.util.UUID

data class Deltakerliste(
    val id: UUID,
    val status: GjennomforingStatusType,
    val sluttDato: LocalDate? = null,
    val oppstart: Oppstartstype,
    val arrangor: Arrangor,
    val pameldingstype: GjennomforingPameldingType,
)
