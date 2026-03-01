package no.nav.amt.lib.models.person.dto

import no.nav.amt.lib.models.person.NavAnsatt
import java.util.UUID

data class NavAnsattDto(
    val id: UUID,
    val navident: String,
    val navn: String,
    val epost: String?,
    val telefon: String?,
    val navEnhetId: UUID?,
) {
    fun toModel() = NavAnsatt(
        id = id,
        navIdent = navident,
        navn = navn,
        epost = epost,
        telefon = telefon,
        navEnhetId = navEnhetId,
    )
}
