package no.nav.amt.internapi.deltaker.request

import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import java.util.UUID

data class EndretPrisinfoRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val prisinfo: PrisinformasjonDto,
    val begrunnelse: String?, // påkrevd i frontend, men følger samme mønster som øvrige endringer
    val prisinformasjonId: UUID? = null,
) : EndringRequest
