package no.nav.amt.internapi.deltaker.request

import java.util.UUID

/**
 * Felles grensesnitt for Endring som kan være basert på et Forslag.
 * Endring kan være basert på et Forslag(fra tiltaksarrangør), eller være en Endring uten tidligere Forslag
 */
sealed interface EndringForslagRequest : EndringRequest {
    val forslagId: UUID?
}
