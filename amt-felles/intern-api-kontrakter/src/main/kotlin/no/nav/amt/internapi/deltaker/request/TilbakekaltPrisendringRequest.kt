package no.nav.amt.internapi.deltaker.request

import java.util.UUID

data class TilbakekaltPrisendringRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    /**
     * null i det requestet går fra amt-deltaker-bff til amt-deltaker
     * populeres med ID til nyeste pending prisinformasjon av amt-deltaker underveis i flyten
     */
    val prisinformasjonId: UUID? = null,
) : EndringRequest
