package no.nav.amt.deltaker.bff.veileder.api.request

import java.time.LocalDate

data class OppdaterEnkeltplassKladdRequest(
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val prisinformasjon: String?,
    val beskrivelse: String?, // dette er annet beskrivelse i innhold
)
