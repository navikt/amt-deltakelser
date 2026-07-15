package no.nav.amt.deltaker.repository.dbo

import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader.Aarsak
import java.util.UUID

data class PrisinfoDbo(
    val id: UUID = UUID.randomUUID(),
    val gjennomforingId: UUID,
    val okonomiGodkjent: Boolean,
    val prisinfoJsonSubtype: String,
    val anskaffelsePris: Int? = null,
    val tilleggsopplysninger: String? = null,
    val ingenkostnaderAarsak: Aarsak? = null,
)
