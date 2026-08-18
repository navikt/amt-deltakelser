package no.nav.amt.aktivitetskort.client

import no.nav.amt.aktivitetskort.client.request.HentAktivitetIdRequest
import no.nav.amt.lib.spring.boot.client.toExternalServiceException
import no.nav.amt.person.service.clients.AKTIVITET_ARENA_ACL_CLIENT_ID
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
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
    } catch (e: RestClientException) {
        throw e.toExternalServiceException(
            serviceName = AKTIVITET_ARENA_ACL_CLIENT_ID,
            action = "hente aktivitetId for Arena-ID $arenaId",
        )
    }
}
