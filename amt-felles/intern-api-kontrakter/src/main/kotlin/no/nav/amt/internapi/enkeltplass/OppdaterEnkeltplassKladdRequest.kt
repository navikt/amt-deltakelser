package no.nav.amt.internapi.enkeltplass

import java.time.LocalDate

data class OppdaterEnkeltplassKladdRequest(
    val beskrivelse: String?, // dette er annet beskrivelse i innhold
    val prisinformasjon: String?,
    val arrangorUnderenhet: String?,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
)
