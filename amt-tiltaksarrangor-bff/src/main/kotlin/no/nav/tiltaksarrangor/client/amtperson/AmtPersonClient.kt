package no.nav.tiltaksarrangor.client.amtperson

import no.nav.amt.lib.models.deltaker.Kontaktinformasjon
import no.nav.tiltaksarrangor.client.toExternalServiceException
import no.nav.tiltaksarrangor.consumer.model.NavEnhet
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import java.util.UUID

@Service
class AmtPersonClient(
    private val api: AmtPersonApi,
) {
    fun hentEnhet(id: UUID): NavEnhet = try {
        api.hentEnhet(id).body?.toNavEnhet()
            ?: throw RuntimeException("Kunne ikke hente Nav-enhet fra amt-person-service")
    } catch (e: RestClientException) {
        throw e.toExternalServiceException(
            serviceName = SERVICE_NAME,
            action = "hente Nav-enhet",
            unauthorizedMessage = "Ikke tilgang til å hente Nav-enhet fra amt-person-service",
        )
    }

    fun hentNavAnsatt(id: UUID): NavAnsattResponse = try {
        api.hentNavAnsatt(id).body
            ?: throw RuntimeException("Kunne ikke hente Nav-ansatt fra amt-person-service")
    } catch (e: RestClientException) {
        throw e.toExternalServiceException(
            serviceName = SERVICE_NAME,
            action = "hente Nav-ansatt",
            unauthorizedMessage = "Ikke tilgang til å hente Nav-ansatt fra amt-person-service",
        )
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
        private const val SERVICE_NAME = "amt-person-service"
        private const val KONTAKTINFO_ERROR_MSG = "Kunne ikke hente kontaktinformasjon fra amt-person-service."
    }
}
