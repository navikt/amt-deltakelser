package no.nav.amt.deltaker.repository.dbo

import java.util.UUID

data class PrisinfoDbo(
    val id: UUID = UUID.randomUUID(),
    val gjennomforingId: UUID,
    val status: PrisinfoStatus = PrisinfoStatus.KLADD_UTKAST,
    val prisinfoJsonSubtype: String,
    val anskaffelsePris: Int? = null,
    val tilleggsopplysninger: String? = null,
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
