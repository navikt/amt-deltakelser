package no.nav.amt.aktivitetskort.client

import no.nav.amt.aktivitetskort.client.request.PersonRequest
import no.nav.amt.aktivitetskort.client.response.OppfolgingPeriodeResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

@HttpExchange("/veilarboppfolging")
@ClientRegistrationId("veilarboppfolging")
interface VeilarboppfolgingApi {
    @PostExchange("/api/v3/oppfolging/hent-gjeldende-periode")
    fun hentGjeldendePeriode(
        @RequestBody request: PersonRequest,
    ): ResponseEntity<OppfolgingPeriodeResponse>
}
