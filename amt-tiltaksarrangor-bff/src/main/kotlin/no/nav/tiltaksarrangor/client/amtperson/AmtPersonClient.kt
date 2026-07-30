package no.nav.tiltaksarrangor.client.amtperson

import no.nav.amt.lib.models.deltaker.Kontaktinformasjon
import no.nav.tiltaksarrangor.consumer.model.NavEnhet
import no.nav.tiltaksarrangor.model.exceptions.UnauthorizedException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException
import java.util.UUID

@Service
class AmtPersonClient(
    private val api: AmtPersonApi,
) {
    fun hentEnhet(id: UUID): NavEnhet = try {
        api.hentEnhet(id).body?.toNavEnhet()
            ?: throw RuntimeException("Kunne ikke hente NAV-enhet fra amt-person-service")
    } catch (e: RestClientResponseException) {
        when (e.statusCode) {
            HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN ->
                throw UnauthorizedException("Ikke tilgang til å hente NAV-enhet fra amt-person-service")
            else -> throw RuntimeException("Kunne ikke hente NAV-enhet fra amt-person-service. Status=${e.statusCode.value()}", e)
        }
    }

    fun hentNavAnsatt(id: UUID): NavAnsattResponse = try {
        api.hentNavAnsatt(id).body
            ?: throw RuntimeException("Kunne ikke hente NAV-ansatt fra amt-person-service")
    } catch (e: RestClientResponseException) {
        when (e.statusCode) {
            HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN ->
                throw UnauthorizedException("Ikke tilgang til å hente NAV-ansatt fra amt-person-service")
            else -> throw RuntimeException("Kunne ikke hente NAV-ansatt fra amt-person-service. Status=${e.statusCode.value()}", e)
        }
    }

    fun hentOppdatertKontaktinfo(personident: String): Result<Kontaktinformasjon> =
        hentOppdatertKontaktinfo(setOf(personident)).mapCatching {
            it[personident] ?: throw NoSuchElementException("Klarte ikke hente kontaktinformasjon for person med ident")
        }

    fun hentOppdatertKontaktinfo(personidenter: Set<String>): Result<Map<String, Kontaktinformasjon>> = runCatching {
        api.hentKontaktinformasjon(personidenter).body
            ?: throw RuntimeException(KONTAKTINFO_ERROR_MSG)
    }

    companion object {
        private const val KONTAKTINFO_ERROR_MSG = "Kunne ikke hente kontaktinformasjon fra amt-person-service."
    }
}
