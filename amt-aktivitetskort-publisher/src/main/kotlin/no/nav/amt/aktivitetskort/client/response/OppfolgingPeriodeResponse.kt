package no.nav.amt.aktivitetskort.client.response

import java.time.ZonedDateTime
import java.util.UUID

data class OppfolgingPeriodeResponse(
    val uuid: UUID,
    val startDato: ZonedDateTime,
    val sluttDato: ZonedDateTime?,
)
