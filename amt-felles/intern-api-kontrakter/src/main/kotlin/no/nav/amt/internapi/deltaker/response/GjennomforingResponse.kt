package no.nav.amt.internapi.deltaker.response

import no.nav.amt.internapi.enkeltplass.PrisinformasjonDto
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import java.time.LocalDate
import java.util.UUID

data class GjennomforingResponse(
    val id: UUID,
    val type: GjennomforingType,
    val tiltakstype: Tiltakstype,
    val navn: String,
    val status: GjennomforingStatusType,
    val startDato: LocalDate?,
    val sluttDato: LocalDate?,
    val antallPlasser: Int?,
    val oppstart: Oppstartstype?,
    val apentForPamelding: Boolean,
    val oppmoteSted: String?,
    val arrangor: ArrangorResponse?,
    val pameldingstype: GjennomforingPameldingType?, // TODO: Denne bør ikke være nullable
    val kodeverkValg: Set<UUID> = emptySet(),
    val sertifiseringValg: Set<SertifiseringValg> = emptySet(),
    val prisinformasjon: PrisinformasjonDto? = null,
)
