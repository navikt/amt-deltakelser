package no.nav.amt.deltaker.repository.dbo

import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader.Aarsak
import java.util.UUID

data class PrisinfoUpsertDbo(
    val id: UUID = UUID.randomUUID(),
    val status: PrisinfoDbo.PrisinfoStatus = PrisinfoDbo.PrisinfoStatus.SENDT,
    val prisinfoJsonSubtype: String,
    val anskaffelsePris: Int? = null,
    val tilleggsopplysninger: String? = null,
    val ingenkostnaderAarsak: Aarsak? = null,
)
