package no.nav.amt.deltaker.enkeltplass

import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingDecoratedRequest
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingRequest
import java.time.LocalDate

data class EnkeltplassPameldingMedDatoer(
    val endretAv: String,
    val endretAvEnhet: String,
    val request: EnkeltplassPameldingRequest,
    val startdato: LocalDate,
    val sluttdato: LocalDate,
) {
    constructor(request: EnkeltplassPameldingDecoratedRequest) : this(
        endretAv = request.endretAv,
        endretAvEnhet = request.endretAvEnhet,
        request = request.wrappedRequest,
        startdato = requireNotNull(request.wrappedRequest.startdato) { "Startdato må oppgis" },
        sluttdato = requireNotNull(request.wrappedRequest.sluttdato) { "Sluttdato må oppgis" },
    ) {
        require(!sluttdato.isBefore(startdato)) { "Sluttdato kan ikke være før startdato" }
    }
}
