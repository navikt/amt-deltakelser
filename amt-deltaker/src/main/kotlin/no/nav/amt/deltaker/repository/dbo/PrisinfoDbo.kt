package no.nav.amt.deltaker.repository.dbo

import no.nav.amt.lib.models.deltakerliste.Prisinformasjon

data class PrisinfoDbo(
    val prisinfoJsonSubtype: String,
    val anskaffelsePris: Int? = null,
    val tilleggsopplysninger: String? = null,
    val ingenkostnaderAarsak: Prisinformasjon.IngenKostnader.Aarsak? = null,
)
