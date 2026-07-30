package no.nav.tiltaksarrangor.client.amtarrangor

import no.nav.tiltaksarrangor.client.amtarrangor.dto.ArrangorMedOverordnetArrangor
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange

@HttpExchange
@ClientRegistrationId("amt-arrangor-aad")
interface HentArrangorApi {
    @GetExchange("/{orgnummer}")
    fun getArrangor(
        @PathVariable orgnummer: String,
    ): ArrangorMedOverordnetArrangor?
}
