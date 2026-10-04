package no.nav.tiltaksarrangor.client.amtarrangor

import no.nav.amt.lib.spring.boot.client.exception.UpstreamServiceException
import no.nav.amt.lib.spring.boot.client.executeUpstreamCall
import no.nav.tiltaksarrangor.client.AMT_ARRANGOR_AAD_CLIENT_ID
import no.nav.tiltaksarrangor.client.amtarrangor.dto.ArrangorMedOverordnetArrangor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class HentArrangorClient(
    private val api: HentArrangorApi,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getArrangor(orgnummer: String): ArrangorMedOverordnetArrangor? = try {
        executeUpstreamCall(
            serviceName = AMT_ARRANGOR_AAD_CLIENT_ID,
            operation = "hente arrangør",
        ) { api.getArrangor(orgnummer) }
    } catch (e: UpstreamServiceException) {
        if (e.statusCode == 404) {
            val message = "Arrangør med orgnummer $orgnummer finnes ikke hos amt-arrangor."
            log.info(message)
            throw NoSuchElementException(message)
        }
        throw e
    }
}
