package no.nav.amt.internapi.deltaker.request

import java.time.LocalDate
import java.util.UUID

data class DeltakelsesmengdeRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    override val forslagId: UUID?,
    val deltakelsesprosent: Int?,
    val dagerPerUke: Int?,
    val begrunnelse: String?,
    val gyldigFra: LocalDate?,
    val pavirkerPris: Boolean?,
) : EndringForslagRequest
