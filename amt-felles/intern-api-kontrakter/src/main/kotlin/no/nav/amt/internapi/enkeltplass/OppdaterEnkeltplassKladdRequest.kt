package no.nav.amt.internapi.enkeltplass

import no.nav.amt.lib.utils.trimToNull
import java.time.LocalDate

data class OppdaterEnkeltplassKladdRequest(
    val beskrivelse: String?, // dette er annet beskrivelse i innhold
    val prisinformasjon: String?,
    val arrangorUnderenhet: String?,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
) {
    fun sanitized() = copy(
        beskrivelse = beskrivelse.trimToNull(),
        prisinformasjon = prisinformasjon.trimToNull(),
        arrangorUnderenhet = arrangorUnderenhet.trimToNull(),
    )
}
