package no.nav.tiltaksarrangor.client.amtarrangor

import no.nav.tiltaksarrangor.client.amtarrangor.dto.ArrangorMedOverordnetArrangor
import no.nav.tiltaksarrangor.model.exceptions.UnauthorizedException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException

@Service
class HentArrangorClient(
    private val api: HentArrangorApi,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getArrangor(orgnummer: String): ArrangorMedOverordnetArrangor? = try {
        api.getArrangor(orgnummer)
    } catch (e: RestClientResponseException) {
        when (e.statusCode) {
            HttpStatus.NOT_FOUND -> {
                val message = "Arrangør med orgnummer $orgnummer finnes ikke hos amt-arrangør."
                log.info(message)
                throw NoSuchElementException(message)
            }
            HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN ->
                throw UnauthorizedException("Uautorisert tilgang ved henting av arrangør med orgnummer $orgnummer fra amt-arrangør.")
            else -> {
                log.error("Feil ved henting av arrangør med orgnummer $orgnummer fra amt-arrangør. Responsekode: ${e.statusCode.value()}")
                throw RuntimeException("Feil ved henting av arrangør med orgnummer $orgnummer fra amt-arrangør. Status=${e.statusCode.value()}", e)
            }
        }
    }
}
