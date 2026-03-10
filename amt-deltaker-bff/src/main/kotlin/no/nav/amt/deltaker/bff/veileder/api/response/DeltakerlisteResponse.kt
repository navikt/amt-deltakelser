package no.nav.amt.deltaker.bff.veileder.api.response

import no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.getInnholdselementer
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.DeltakerRegistreringInnhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Innholdselement
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.time.LocalDate
import java.util.UUID

data class DeltakerlisteResponse(
    val deltakerlisteId: UUID,
    val deltakerlisteNavn: String, // Arbeidsmarkedsopplæring (enkeltplass), tiltakstypenavn
    val tiltakskode: Tiltakskode,
    val arrangorNavn: String, // Smart Læring
    val oppstartstype: Oppstartstype?, // løpende
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val status: GjennomforingStatusType?,
    val tilgjengeligInnhold: TilgjengeligInnhold?,
    val erEnkeltplassUtenRammeavtale: Boolean,
    val oppmoteSted: String?,
    val pameldingstype: GjennomforingPameldingType,
)

data class TilgjengeligInnhold(
    val ledetekst: String?,
    val innhold: List<Innholdselement>,
) {
    companion object {
        // TODO: Her bør man ikke instansiere objektet hvis det verken er ledetekst eller innholdselementer
        fun fromDeltakerRegistreringInnhold(
            innhold: DeltakerRegistreringInnhold?,
            tiltakstype: Tiltakskode,
        ) = TilgjengeligInnhold(
            ledetekst = innhold?.ledetekst,
            innhold = getInnholdselementer(innhold?.innholdselementer, tiltakstype),
        )
    }
}
