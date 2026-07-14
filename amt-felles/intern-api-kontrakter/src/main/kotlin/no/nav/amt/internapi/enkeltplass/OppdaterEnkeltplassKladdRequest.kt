package no.nav.amt.internapi.enkeltplass

import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.utils.trimToNull
import java.time.LocalDate
import java.util.UUID

data class OppdaterEnkeltplassKladdRequest(
    val beskrivelse: String?, // dette er annet beskrivelse i innhold
    val arrangorUnderenhet: String?,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val kodeverkValg: Set<UUID>? = null,
    val sertifiseringValg: Set<SertifiseringValg>? = null,
    val prisinformasjon: PrisinformasjonDto? = null,
    val dagerPerUke: Int? = null,
) {
    fun sanitized() = copy(
        beskrivelse = beskrivelse.trimToNull()?.sanitizeBeskrivelse(),
        arrangorUnderenhet = arrangorUnderenhet.trimToNull()?.sanitizeArrangorUnderenhet(),
        prisinformasjon = prisinformasjon?.sanitize(),
    )
}
