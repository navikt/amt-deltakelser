package no.nav.amt.lib.models.deltaker.internalapis.deltaker.response

import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
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
    /*
        Må vi ha med historikk? Ja, fordi noe informasjon utledes fra historikken i domeneobjektet i amt-deltaker-bff
        Vi bør vurdere hente ut konkrete datapunkter i amt-deltaker så denne kan fjernes.
        historikken blir brukt i frontend vha get /historikk endepunket, så vi kan vurdere om dette responsobjektet eller
        en eget historikk endepunkt med respons skal lages
     */
    val historikk: List<DeltakerHistorikk>,
    val erLaastForEndringer: Boolean,
    val endringsforslagFraArrangor: List<Forslag>,
)
