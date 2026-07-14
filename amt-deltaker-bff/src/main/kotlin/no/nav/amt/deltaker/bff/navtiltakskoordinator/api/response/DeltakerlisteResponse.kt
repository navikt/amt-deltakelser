package no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response

import no.nav.amt.deltaker.bff.navtiltakskoordinator.model.Tiltakskoordinator
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.deltakerliste.tiltakstype.TiltakskodeDto
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
    val koordinatorer: List<TiltakskoordinatorResponse>,
    val erEnkeltplass: Boolean,
) {

    val tiltakskodeDto: TiltakskodeDto = TiltakskodeDto(tiltakskode)

    data class TiltakskoordinatorResponse(
        val id: UUID,
        val navn: String,
        val erAktiv: Boolean,
        val kanFjernes: Boolean,
    ) {
        constructor(model: Tiltakskoordinator) : this(
            id = model.id,
            navn = model.navn,
            erAktiv = model.erAktiv,
            kanFjernes = model.kanFjernes,
        )
    }
}
