package no.nav.amt.deltaker.repository.dbo

import no.nav.amt.internapi.enkeltplass.PrisinformasjonDto

data class PrisinfoDbo(
    val prisinfoJsonSubtype: String,
    val anskaffelsePris: Int? = null,
    val tilleggsopplysninger: String? = null,
    val ingenkostnaderAarsak: PrisinformasjonDto.IngenKostnader.Aarsak? = null,
)
