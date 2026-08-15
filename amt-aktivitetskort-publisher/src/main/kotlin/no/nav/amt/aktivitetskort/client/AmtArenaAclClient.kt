package no.nav.amt.aktivitetskort.client

import no.nav.amt.aktivitetskort.exceptions.HistoriskArenaDeltakerException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException
import java.util.UUID

@Service
class AmtArenaAclClient(
    private val api: AmtArenaAclApi,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getArenaIdForAmtId(amtId: UUID): Long? {
        return try {
            val response = api.getTranslation(amtId)
            val body = response.body ?: return null

            body.arenaId?.let {
                return it.toLong()
            }

            body.arenaHistId?.let {
                val msg = "amtId $amtId tilhører histdeltaker med id $it"
                log.error(msg)
                throw HistoriskArenaDeltakerException(message = msg)
            }

            log.warn("Fant ikke arenaId eller arenaHistId for deltaker med id $amtId")
            null
        } catch (e: RestClientResponseException) {
            throw RuntimeException("Klarte ikke å hente arenaId for AmtId $amtId. Status: ${e.statusCode}", e)
        }
    }
}
