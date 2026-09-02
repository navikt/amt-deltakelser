package no.nav.amt.internapi.deltaker.request

import java.time.LocalDate
import java.util.UUID

data class StartdatoRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    override val forslagId: UUID?,
    val startdato: LocalDate?,
    val sluttdato: LocalDate? = null,
    val begrunnelse: String?,
    val pavirkerPris: Boolean?,
) : EndringForslagRequest
