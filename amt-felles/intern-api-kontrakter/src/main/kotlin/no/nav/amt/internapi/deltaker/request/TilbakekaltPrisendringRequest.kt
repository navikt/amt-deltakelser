package no.nav.amt.internapi.deltaker.request

import java.util.UUID

data class TilbakekaltPrisendringRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val prisinformasjonId: UUID? = null,
) : EndringRequest
