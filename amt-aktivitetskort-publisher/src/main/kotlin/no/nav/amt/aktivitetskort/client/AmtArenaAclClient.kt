package no.nav.amt.aktivitetskort.client

import no.nav.amt.aktivitetskort.exceptions.HistoriskArenaDeltakerException
import no.nav.common.rest.client.RestClient.baseClient
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.util.UUID
import java.util.function.Supplier

class AmtArenaAclClient(
    private val baseUrl: String,
    private val tokenProvider: Supplier<String>,
    private val objectMapper: ObjectMapper,
    private val httpClient: OkHttpClient = baseClient(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getArenaIdForAmtId(amtId: UUID): Long? {
        val request = Request
            .Builder()
            .url("$baseUrl/api/v2/translation/$amtId")
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.get())
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Klarte ikke å hente arenaId for AmtId $amtId. Status: ${response.code}")
            }

            val hentArenaIdV2Response = objectMapper.readValue<HentArenaIdV2Response>(response.body.string())

            hentArenaIdV2Response.arenaId?.let {
                return it.toLong()
            }

            hentArenaIdV2Response.arenaHistId?.let {
                val msg = "amtId $amtId tilhører histdeltaker med id $it"
                log.error(msg)
                throw HistoriskArenaDeltakerException(message = msg)
            }

            log.warn("Fant ikke arenaId eller arenaHistId for deltaker med id $amtId")
            return null
        }
    }

    private data class HentArenaIdV2Response(
        val arenaId: String?,
        val arenaHistId: String?,
    )
}
