package no.nav.amt.deltaker.bff.tiltakskoordinator.api.response

import no.nav.amt.deltaker.bff.tiltakskoordinator.model.Tiltakskoordinator
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.time.LocalDate
import java.util.UUID

data class DeltakerlisteResponse(
    val id: UUID,
    val navn: String,
    val tiltakskode: Tiltakskode,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val oppstartstype: Oppstartstype?,
    val apentForPamelding: Boolean,
    val antallPlasser: Int?,
    val pameldingstype: GjennomforingPameldingType,
    val koordinatorer: List<Tiltakskoordinator>,
)
