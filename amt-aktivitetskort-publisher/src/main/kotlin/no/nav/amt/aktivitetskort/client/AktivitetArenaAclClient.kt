package no.nav.amt.aktivitetskort.client

import no.nav.common.rest.client.RestClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.util.UUID
import java.util.function.Supplier

/**
 *  Klient for å håndtere kall mot Aktivitet Arena ACL
 *
 *  Swagger: https://aktivitet-arena-acl.intern.dev.nav.no/internal/swagger-ui/index.html#/TranslationController/finnAktivitetsIdForArenaId
 */
class AktivitetArenaAclClient(
    private val baseUrl: String,
    private val tokenProvider: Supplier<String>,
    private val objectMapper: ObjectMapper,
    private val httpClient: OkHttpClient = RestClient.baseClient(),
) {
    fun getAktivitetIdForArenaId(arenaId: Long): UUID {
        val request = okhttp3.Request
            .Builder()
            .url("$baseUrl/api/translation/arenaid")
            .header("Accept", APPLICATION_JSON_VALUE)
            .header("Authorization", "Bearer " + tokenProvider.get())
            .header("Content-Type", APPLICATION_JSON_VALUE)
            .post(HentAktivitetIdRequest(arenaId).toRequest(objectMapper))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Klarte ikke å hente aktivitetId for ArenaId. Status: ${response.code}")
            }

            return objectMapper.readValue(response.body.string())
        }
    }

    data class HentAktivitetIdRequest(
        val arenaId: Long,
        val aktivitetKategori: String = "TILTAKSAKTIVITET",
    ) {
        fun toRequest(objectMapper: ObjectMapper) = objectMapper
            .writeValueAsString(this)
            .toRequestBody(APPLICATION_JSON_VALUE.toMediaType())
    }
}
