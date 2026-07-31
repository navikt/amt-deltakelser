package no.nav.amt.aktivitetskort.client

import no.nav.amt.aktivitetskort.exceptions.HistoriskArenaDeltakerException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.toEntity
import java.util.UUID

@Service
class AmtArenaAclClient(
    @Value($$"${amt.arena-acl.url}") baseUrl: String,
    restClientBuilder: RestClient.Builder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient: RestClient = restClientBuilder
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build()

    fun getArenaIdForAmtId(amtId: UUID): Long? {
        return try {
            val response = restClient
                .get()
                .uri("/api/v2/translation/{amtId}", amtId)
                .retrieve()
                .toEntity<HentArenaIdV2Response>()

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

    private data class HentArenaIdV2Response(
        val arenaId: String?,
        val arenaHistId: String?,
    )
}
