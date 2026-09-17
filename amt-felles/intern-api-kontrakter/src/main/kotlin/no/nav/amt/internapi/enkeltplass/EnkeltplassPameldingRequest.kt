package no.nav.amt.internapi.enkeltplass

import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import java.time.LocalDate
import java.util.UUID

data class EnkeltplassPameldingRequest(
    val beskrivelse: String,
    val arrangorUnderenhet: String,
    val startdato: LocalDate,
    val sluttdato: LocalDate,
    val kodeverkValg: Set<UUID>? = null,
    val sertifiseringValg: Set<SertifiseringValg>? = null,
    val prisinformasjon: PrisinformasjonDto,
    val dagerPerUke: Int? = null,
) {
    init {
        require(!sluttdato.isBefore(startdato)) { "Sluttdato kan ikke være før startdato" }
    }

    fun sanitized() = copy(
        beskrivelse = beskrivelse.sanitizeBeskrivelse(),
        arrangorUnderenhet = arrangorUnderenhet.sanitizeArrangorUnderenhet(),
        prisinformasjon = prisinformasjon.sanitize(),
    )
}
