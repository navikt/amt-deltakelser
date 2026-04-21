package no.nav.amt.internapi.deltaker.response

import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import java.util.UUID

data class DeltakerHistorikkDataResponse(
    val historikk: List<DeltakerHistorikk>,
    val arrangornavn: String,
    val oppstartstype: Oppstartstype?,
    val pameldingstype: GjennomforingPameldingType?,
    val ansatte: Map<UUID, NavAnsatt>,
    val enheter: Map<UUID, NavEnhet>,
)
