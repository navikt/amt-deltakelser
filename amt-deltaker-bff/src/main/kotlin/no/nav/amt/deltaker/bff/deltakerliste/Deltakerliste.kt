package no.nav.amt.deltaker.bff.deltakerliste

import no.nav.amt.deltaker.bff.utils.toTitleCase
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
    // Merkelig datastruktur som lager behov for å joine samme tabell flere ganger
    // Erstattet i GjennomforingModel og utledes i amt-deltaker
    data class Arrangor(
        val arrangor: no.nav.amt.lib.models.deltaker.Arrangor,
        val overordnetArrangorNavn: String?,
    ) {
        fun getArrangorNavn(): String = toTitleCase(
            if (overordnetArrangorNavn.isNullOrEmpty() || overordnetArrangorNavn == UKJENT_VIRKSOMHET) {
                arrangor.navn
            } else {
                overordnetArrangorNavn
            },
        )

        companion object {
            private const val UKJENT_VIRKSOMHET = "Ukjent Virksomhet"
        }
    }

    // Flyttet til lib
    fun deltakerAdresseDeles() = tiltakUtenDeltakerAdresset.none { it == this.tiltak.tiltakskode }
}

// Flyttet til lib
private val tiltakUtenDeltakerAdresset = setOf(
    Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK,
    Tiltakskode.JOBBKLUBB,
    Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
    Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
)
