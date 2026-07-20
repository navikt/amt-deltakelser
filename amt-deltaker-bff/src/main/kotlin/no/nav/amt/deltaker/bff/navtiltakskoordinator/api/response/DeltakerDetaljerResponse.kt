package no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response

import no.nav.amt.deltaker.bff.commonresponse.TiltakskodeResponse
import no.nav.amt.deltaker.bff.veileder.api.response.ForslagResponse
import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.person.Beskyttelsesmarkering
import java.time.LocalDate
import java.util.UUID

data class DeltakerDetaljerResponse(
    val id: UUID,
    val fornavn: String,
    val mellomnavn: String? = null,
    val etternavn: String,
    val fodselsnummer: String?,
    val status: DeltakerStatusResponse,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val navEnhet: String?,
    val navVeileder: NavVeilederResponse,
    val beskyttelsesmarkering: List<Beskyttelsesmarkering>,
    val vurdering: VurderingResponse?,
    val innsatsgruppe: Innsatsgruppe?,
    val tiltakskode: TiltakskodeResponse,
    val tilgangTilBruker: Boolean,
    val aktiveForslag: List<ForslagResponse>,
    val ulesteHendelser: List<UlestHendelseResponse>,
    val oppstartstype: Oppstartstype?,
    val pameldingstype: GjennomforingPameldingType,
    val deltakelsesinnhold: String?,
    val erEnkeltplass: Boolean,
)
