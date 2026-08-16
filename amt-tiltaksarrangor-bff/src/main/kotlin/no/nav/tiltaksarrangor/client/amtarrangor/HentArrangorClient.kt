package no.nav.tiltaksarrangor.client.amtarrangor

import no.nav.tiltaksarrangor.client.AMT_ARRANGOR_AAD_CLIENT_ID
import no.nav.tiltaksarrangor.client.amtarrangor.dto.ArrangorMedOverordnetArrangor
import no.nav.tiltaksarrangor.client.toExternalServiceException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

@Service
class HentArrangorClient(
    private val api: HentArrangorApi,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getArrangor(orgnummer: String): ArrangorMedOverordnetArrangor? = try {
        api.getArrangor(orgnummer)
    } catch (e: RestClientException) {
        if (e is RestClientResponseException && e.statusCode == HttpStatus.NOT_FOUND) {
            val message = "Arrangør med orgnummer $orgnummer finnes ikke hos amt-arrangor."
            log.info(message)
            throw NoSuchElementException(message)
        }

        throw e.toExternalServiceException(
            serviceName = AMT_ARRANGOR_AAD_CLIENT_ID,
            action = "hente arrangør med orgnummer $orgnummer",
            unauthorizedMessage = "Uautorisert tilgang ved henting av arrangør med orgnummer $orgnummer fra amt-arrangor.",
        )
    }
}
