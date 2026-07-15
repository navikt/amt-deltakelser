package no.nav.amt.deltaker.bff.commonresponse

import no.nav.amt.deltaker.bff.veileder.api.response.OpplaringKategoriseringValgResponse
import no.nav.amt.deltaker.bff.veileder.api.response.TilgjengeligInnholdResponse
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.time.LocalDate
import java.util.UUID

data class DeltakerlisteResponse(
    val deltakerlisteId: UUID,
    val deltakerlisteNavn: String,
    val tiltakskode: Tiltakskode,
    val arrangorNavn: String, // skal fjernes
    val arrangor: ArrangorResponse?,
    val oppstartstype: Oppstartstype?,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val status: GjennomforingStatusType?,
    val tilgjengeligInnhold: TilgjengeligInnholdResponse?,
    val erEnkeltplass: Boolean,
    val oppmoteSted: String?,
    val pameldingstype: GjennomforingPameldingType,
    val opplaringKategoriseringValg: OpplaringKategoriseringValgResponse? = null,
    val prisinformasjon: PrisinformasjonResponse? = null,
) {
    data class ArrangorResponse(
        val navn: String,
        val organisasjonsnummer: String,
    )
}
