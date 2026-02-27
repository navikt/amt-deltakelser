package no.nav.amt.deltaker.bff.deltakerliste

import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import java.time.LocalDate
import java.util.UUID

data class Deltakerliste(
    val id: UUID,
    val tiltak: Tiltakstype,
    val navn: String,
    val status: GjennomforingStatusType?,
    val startDato: LocalDate?,
    val sluttDato: LocalDate? = null,
    val oppstart: Oppstartstype?,
    val arrangor: Arrangor,
    val apentForPamelding: Boolean,
    val antallPlasser: Int?,
    val oppmoteSted: String?,
    val pameldingstype: GjennomforingPameldingType?,
) {
    data class Arrangor(
        val arrangor: no.nav.amt.lib.models.deltaker.Arrangor,
        val overordnetArrangorNavn: String?,
    )

    fun deltakerAdresseDeles() = tiltakUtenDeltakerAdresset.none { it == this.tiltak.tiltakskode }
}

private val tiltakUtenDeltakerAdresset = setOf(
    Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK,
    Tiltakskode.JOBBKLUBB,
    Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
    Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
)
