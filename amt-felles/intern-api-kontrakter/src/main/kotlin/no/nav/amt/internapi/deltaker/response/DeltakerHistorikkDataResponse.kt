package no.nav.amt.internapi.deltaker.response

import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet

data class DeltakerHistorikkDataResponse(
    val historikk: List<DeltakerHistorikk>,
    val arrangornavn: String,
    val oppstartstype: Oppstartstype?,
    val ansatte: List<NavAnsatt>,
    val enheter: List<NavEnhet>,
)

