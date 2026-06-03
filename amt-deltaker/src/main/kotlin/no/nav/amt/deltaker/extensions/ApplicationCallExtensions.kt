package no.nav.amt.deltaker.extensions

import io.ktor.server.application.ApplicationCall
import java.util.UUID

private const val DELTAKER_ID_PARAM = "deltakerId"
private const val GJENNOMFORING_ID_PARAM = "gjennomforingId"
private const val FORSLAG_ID_PARAM = "forslagId"

fun ApplicationCall.getDeltakerId(): UUID = UUID.fromString(this.parameters[DELTAKER_ID_PARAM])

fun ApplicationCall.getForslagId(): UUID = UUID.fromString(this.parameters[FORSLAG_ID_PARAM])

fun ApplicationCall.getGjennomforingId(): UUID = UUID.fromString(this.parameters[GJENNOMFORING_ID_PARAM])
