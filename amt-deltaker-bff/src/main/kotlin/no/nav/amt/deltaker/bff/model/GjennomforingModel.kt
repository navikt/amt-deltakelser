package no.nav.amt.deltaker.bff.model

import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import java.time.LocalDate
import java.util.UUID

data class GjennomforingModel(
    val id: UUID,
    val type: GjennomforingType,
    val tiltak: Tiltakstype,
    val navn: String,
    val status: GjennomforingStatusType,
    val startDato: LocalDate?,
    val sluttDato: LocalDate? = null,
    val oppstart: Oppstartstype?,
    val arrangor: ArrangorModel?,
    val apentForPamelding: Boolean,
    val oppmoteSted: String?,
    val pameldingstype: GjennomforingPameldingType?,
    val opplaringKategoriseringValg: OpplaringKategoriseringValg? = null,
    val prisinformasjon: PrisinformasjonDto? = null,
    val prisinformasjonTilGodkjenning: PrisinformasjonDto? = null,
    val prisinformasjonBegrunnelse: String? = null,
) {
    val erEnkeltplass = type == GjennomforingType.Enkeltplass
}
