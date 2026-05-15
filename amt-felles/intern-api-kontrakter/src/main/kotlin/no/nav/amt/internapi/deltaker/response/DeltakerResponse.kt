package no.nav.amt.internapi.deltaker.response

import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.Kilde
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class DeltakerResponse(
    val id: UUID,
    val status: DeltakerStatus,
    val navBruker: NavBrukerResponse,
    val gjennomforing: GjennomforingResponse,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val dagerPerUke: Float?,
    val deltakelsesprosent: Float?,
    val bakgrunnsinformasjon: String?,
    val deltakelsesinnhold: Deltakelsesinnhold?,
    val vedtaksinformasjon: VedtaksinformasjonResponse?,
    val erManueltDeltMedArrangor: Boolean,
    val kilde: Kilde,
    val sistEndret: LocalDateTime,
    val opprettet: LocalDateTime,
    val soktInnDato: LocalDate?, // tiltakskoordinator trenger
    val deltakelsesmengder: DeltakelsesmengderResponse?, // veileder trenger
    val erLaastForEndringer: Boolean,
    val endringsforslagFraArrangor: List<Forslag>,
    val prisinformasjon: String?,
    val sisteVurdering: VurderingResponse?,
    val importertFraArena: ImportertFraArena?,
)
