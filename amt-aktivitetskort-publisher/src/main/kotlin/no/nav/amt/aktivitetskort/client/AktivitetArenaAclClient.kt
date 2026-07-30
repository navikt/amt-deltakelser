package no.nav.amt.aktivitetskort.client

import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException
import java.util.UUID

/**
 *  Klient for å håndtere kall mot Aktivitet Arena ACL
 *
 *  Swagger: https://aktivitet-arena-acl.intern.dev.nav.no/internal/swagger-ui/index.html#/TranslationController/finnAktivitetsIdForArenaId
 */
@Service
class AktivitetArenaAclClient(
    private val api: AktivitetArenaAclApi,
) {
    fun getAktivitetIdForArenaId(arenaId: Long): UUID = try {
        api.getAktivitetIdForArenaId(HentAktivitetIdRequest(arenaId))
    } catch (e: RestClientResponseException) {
        throw RuntimeException("Klarte ikke å hente aktivitetId for ArenaId. Status: ${e.statusCode}", e)
    }
}
