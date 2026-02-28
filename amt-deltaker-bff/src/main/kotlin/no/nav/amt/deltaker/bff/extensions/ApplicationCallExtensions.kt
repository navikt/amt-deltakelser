package no.nav.amt.deltaker.bff.extensions

import io.ktor.server.application.ApplicationCall
import no.nav.amt.deltaker.bff.deltaker.api.headerNotNull
import java.util.UUID

private const val DELTAKER_ID_PARAM = "deltakerId"
private const val FORSLAG_ID_PARAM = "forslagId"
private const val AKTIV_ENHET_HEADER = "aktiv-enhet"

fun ApplicationCall.getDeltakerId(): UUID = UUID.fromString(this.parameters[DELTAKER_ID_PARAM])

fun ApplicationCall.getForslagId(): UUID = UUID.fromString(this.parameters[FORSLAG_ID_PARAM])

fun ApplicationCall.getEnhetsnummer() = this.request.headerNotNull(AKTIV_ENHET_HEADER)
