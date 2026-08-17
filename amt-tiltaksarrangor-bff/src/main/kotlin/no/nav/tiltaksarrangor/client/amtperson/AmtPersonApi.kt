package no.nav.tiltaksarrangor.client.amtperson

import no.nav.amt.lib.models.deltaker.Kontaktinformasjon
import no.nav.tiltaksarrangor.client.AMT_PERSON_SERVICE_CLIENT_ID
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange
import java.util.UUID

@HttpExchange
@ClientRegistrationId(AMT_PERSON_SERVICE_CLIENT_ID)
interface AmtPersonApi {
    @GetExchange("/api/nav-enhet/{id}")
    fun hentEnhet(
        @PathVariable id: UUID,
    ): ResponseEntity<NavEnhetResponse>

    @GetExchange("/api/nav-ansatt/{id}")
    fun hentNavAnsatt(
        @PathVariable id: UUID,
    ): ResponseEntity<NavAnsattResponse>

    @PostExchange("/api/nav-bruker/kontaktinformasjon")
    fun hentKontaktinformasjon(
        @RequestBody personidenter: Set<String>,
    ): ResponseEntity<Map<String, Kontaktinformasjon>>
}
