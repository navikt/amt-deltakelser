package no.nav.amt.deltaker.repository.dbo

import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader.Aarsak
import java.util.UUID

data class PrisinfoDbo(
    val id: UUID = UUID.randomUUID(),
    val gjennomforingId: UUID,
    val rolle: Rolle = Rolle.ENDRING,
    val status: PrisinfoStatus = PrisinfoStatus.KLADD_UTKAST,
    val prisinfoJsonSubtype: String,
    val anskaffelsePris: Int? = null,
    val tilleggsopplysninger: String? = null,
    val ingenkostnaderAarsak: Aarsak? = null,
) {
    enum class PrisinfoStatus {
        KLADD_UTKAST,
        SENDT,
        RETURNERT,
        TIL_BEHANDLING,
        SATT_PA_VENT,
        GODKJENT,
    }

    enum class Rolle {
        ENDRING,
        GJELDENDE,
    }
}
