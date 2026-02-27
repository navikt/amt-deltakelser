package no.nav.amt.deltaker.extensions

import io.ktor.server.application.ApplicationCall
import java.util.UUID

private const val DELTAKER_ID_PARAM = "deltakerId"

fun ApplicationCall.getDeltakerId(): UUID = UUID.fromString(this.parameters[DELTAKER_ID_PARAM])
