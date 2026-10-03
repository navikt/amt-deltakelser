package no.nav.amt.aktivitetskort.client

import no.nav.amt.aktivitetskort.client.request.HentAktivitetIdRequest
import no.nav.amt.lib.spring.boot.client.executeUpstreamCallWithRequiredBody
import org.springframework.stereotype.Service
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
    fun getAktivitetIdForArenaId(arenaId: Long): UUID = executeUpstreamCallWithRequiredBody(
        serviceName = AKTIVITET_ARENA_ACL_CLIENT_ID,
        operation = "hente aktivitetId for Arena-ID",
    ) { api.getAktivitetIdForArenaId(HentAktivitetIdRequest(arenaId)) }
}
