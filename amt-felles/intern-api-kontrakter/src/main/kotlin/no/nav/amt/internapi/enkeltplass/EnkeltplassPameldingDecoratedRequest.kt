package no.nav.amt.internapi.enkeltplass

data class EnkeltplassPameldingDecoratedRequest(
    val endretAv: String,
    val endretAvEnhet: String,
    val wrappedRequest: EnkeltplassPameldingRequest,
)
