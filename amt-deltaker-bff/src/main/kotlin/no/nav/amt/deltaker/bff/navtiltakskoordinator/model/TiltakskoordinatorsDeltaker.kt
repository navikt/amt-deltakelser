package no.nav.amt.deltaker.bff.navtiltakskoordinator.model

import no.nav.amt.deltaker.bff.model.Deltakerliste
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelse
import no.nav.amt.internapi.deltaker.response.NavVeilederResponse
import no.nav.amt.internapi.tiltakskoordinator.response.DeltakerOppdateringFeilkode
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Vurdering
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.person.Beskyttelsesmarkering
import no.nav.amt.lib.models.person.NavBruker
import java.time.LocalDate
import java.util.UUID

data class TiltakskoordinatorsDeltaker(
    val id: UUID,
    val navBruker: NavBruker,
    val status: DeltakerStatus,
    val soktInnDato: LocalDate?,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val navEnhet: String?,
    val navVeileder: NavVeilederResponse,
    val beskyttelsesmarkering: List<Beskyttelsesmarkering>,
    val vurdering: Vurdering?,
    val innsatsgruppe: Innsatsgruppe?,
    val deltakerliste: Deltakerliste,
    val erManueltDeltMedArrangor: Boolean,
    val kanEndres: Boolean,
    val feilkode: DeltakerOppdateringFeilkode? = null,
    val ikkeDigitalOgManglerAdresse: Boolean,
    val forslag: List<Forslag>,
    val ulesteHendelser: List<UlestHendelse>,
    val deltakelsesinnhold: Deltakelsesinnhold?,
)
