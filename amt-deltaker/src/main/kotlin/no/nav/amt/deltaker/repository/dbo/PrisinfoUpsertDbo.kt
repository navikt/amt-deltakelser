package no.nav.amt.deltaker.repository.dbo

import java.util.UUID

data class PrisinfoUpsertDbo(
    val id: UUID = UUID.randomUUID(),
    val gjennomforingId: UUID,
    val status: PrisinfoDbo.PrisinfoStatus = PrisinfoDbo.PrisinfoStatus.KLADD_UTKAST,
    val prisinfoJsonSubtype: String,
    val anskaffelsePris: Int? = null,
    val tilleggsopplysninger: String? = null,
    val ingenkostnaderAarsak: PrisinfoDbo.Aarsak? = null,
)
