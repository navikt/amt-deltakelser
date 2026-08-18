package no.nav.tiltaksarrangor.client.amtarrangor

import no.nav.tiltaksarrangor.client.AMT_ARRANGOR_AAD_CLIENT_ID
import no.nav.tiltaksarrangor.client.amtarrangor.dto.ArrangorMedOverordnetArrangor
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange

@HttpExchange
@ClientRegistrationId(AMT_ARRANGOR_AAD_CLIENT_ID)
interface HentArrangorApi {
    @GetExchange("/{orgnummer}")
    fun getArrangor(
        @PathVariable orgnummer: String,
    ): ArrangorMedOverordnetArrangor?
}
