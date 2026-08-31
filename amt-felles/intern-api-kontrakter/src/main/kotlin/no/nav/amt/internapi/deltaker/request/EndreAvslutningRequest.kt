package no.nav.amt.internapi.deltaker.request

import no.nav.amt.lib.models.deltaker.DeltakerEndring
import java.time.LocalDate
import java.util.UUID

data class EndreAvslutningRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    override val forslagId: UUID?,
    val aarsak: DeltakerEndring.Aarsak?,
    val begrunnelse: String?,
    val sluttdato: LocalDate?,
    val harFullfort: Boolean?,
) : EndringForslagRequest
