package no.nav.amt.aktivitetskort.client

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.requiredBody
import java.util.UUID

/**
 *  Klient for å håndtere kall mot Aktivitet Arena ACL
 *
 *  Swagger: https://aktivitet-arena-acl.intern.dev.nav.no/internal/swagger-ui/index.html#/TranslationController/finnAktivitetsIdForArenaId
 */
@Service
class AktivitetArenaAclClient(
    @Value($$"${aktivitet.arena-acl.url}") baseUrl: String,
    restClientBuilder: RestClient.Builder,
) {
    private val restClient: RestClient = restClientBuilder
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build()

    fun getAktivitetIdForArenaId(arenaId: Long): UUID = try {
        restClient
            .post()
            .uri("/api/translation/arenaid")
            .body(HentAktivitetIdRequest(arenaId))
            .retrieve()
            .requiredBody<UUID>()
    } catch (e: RestClientResponseException) {
        throw RuntimeException("Klarte ikke å hente aktivitetId for ArenaId. Status: ${e.statusCode}", e)
    }

    data class HentAktivitetIdRequest(
        val arenaId: Long,
        val aktivitetKategori: String = "TILTAKSAKTIVITET",
    )
}
