package no.nav.amt.internapi.paamelding.request

import java.time.LocalDate

data class OppdaterEnkeltplassKladdRequest(
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val prisinformasjon: String?,
    val beskrivelse: String?, // dette er annet beskrivelse i innhold
)
