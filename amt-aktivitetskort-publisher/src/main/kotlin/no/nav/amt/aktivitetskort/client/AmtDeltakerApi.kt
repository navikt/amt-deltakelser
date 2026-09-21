package no.nav.amt.aktivitetskort.client

import no.nav.amt.internapi.deltaker.response.DeltakerResponse
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import java.util.UUID

@HttpExchange("/deltaker")
@ClientRegistrationId(AMT_DELTAKER_CLIENT_ID)
interface AmtDeltakerApi {
    @GetExchange("/{deltakerId}")
    fun getDeltaker(
        @PathVariable deltakerId: UUID,
    ): DeltakerResponse
}
