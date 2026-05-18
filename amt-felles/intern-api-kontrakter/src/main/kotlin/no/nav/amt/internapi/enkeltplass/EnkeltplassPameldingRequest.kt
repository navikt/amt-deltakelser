package no.nav.amt.internapi.enkeltplass

import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import java.time.LocalDate
import java.util.UUID

// TODO: Denne klassen er veldig lik OppdaterEnkeltplassKladdRequest
data class EnkeltplassPameldingRequest(
    val beskrivelse: String,
    val prisinformasjon: String,
    val arrangorUnderenhet: String,
    val startdato: LocalDate? = null,
    val sluttdato: LocalDate? = null,
    val kodeverkValg: Set<UUID>? = null,
    val sertifiseringValg: Set<SertifiseringValg>? = null,
) {
    fun sanitized() = copy(
        beskrivelse = beskrivelse.sanitizeBeskrivelse(),
        prisinformasjon = prisinformasjon.sanitizePrisinformasjon(),
        arrangorUnderenhet = arrangorUnderenhet.sanitizeArrangorUnderenhet(),
    )
}
