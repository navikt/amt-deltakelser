package no.nav.amt.aktivitetskort.client

import no.nav.amt.aktivitetskort.client.request.HentAktivitetIdRequest
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange
import java.util.UUID

@HttpExchange
@ClientRegistrationId("aktivitet-arena-acl")
interface AktivitetArenaAclApi {
    @PostExchange("/api/translation/arenaid")
    fun getAktivitetIdForArenaId(
        @RequestBody request: HentAktivitetIdRequest,
    ): UUID
}
