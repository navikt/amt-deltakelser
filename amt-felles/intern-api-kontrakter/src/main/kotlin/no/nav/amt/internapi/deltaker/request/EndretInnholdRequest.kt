package no.nav.amt.internapi.deltaker.request

data class EndretInnholdRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val innholdselementer: List<InnholdsElementRequest>,
) : EndringRequest
