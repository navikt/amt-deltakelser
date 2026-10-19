package no.nav.amt.deltaker.repository.dbo

import java.util.UUID

data class PrisinfoDbo(
    val id: UUID = UUID.randomUUID(),
    val gjennomforingId: UUID,
    val status: PrisinfoStatus = PrisinfoStatus.KLADD_UTKAST,
    val prisinfoJsonSubtype: String,
    val anskaffelsePris: Int? = null,
    val tilleggsopplysninger: String? = null,
    val ingenkostnaderAarsak: Aarsak? = null,
    val begrunnelse: String? = null, // har verdi kun ved endringer
) {
    enum class Aarsak {
        OPPLAERINGEN_ER_KOSTNADSFRI,
        OPPLAERINGEN_ER_EGENFINANSIERT,
    }

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
