package no.nav.amt.aktivitetskort.client

import no.nav.amt.aktivitetskort.client.response.HentArenaIdV2Response
import no.nav.amt.person.service.clients.AMT_ARENA_ACL_CLIENT_ID
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import java.util.UUID

@HttpExchange
@ClientRegistrationId(AMT_ARENA_ACL_CLIENT_ID)
interface AmtArenaAclApi {
    @GetExchange("/api/v2/translation/{amtId}")
    fun getTranslation(
        @PathVariable amtId: UUID,
    ): ResponseEntity<HentArenaIdV2Response>
}
