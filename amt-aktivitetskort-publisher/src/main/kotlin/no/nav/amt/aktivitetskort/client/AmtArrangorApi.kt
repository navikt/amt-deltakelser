package no.nav.amt.aktivitetskort.client

import no.nav.amt.aktivitetskort.client.response.ArrangorMedOverordnetArrangorResponse
import no.nav.amt.person.service.clients.AMT_ARRANGOR_CLIENT_ID
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import java.util.UUID

@HttpExchange("/api/service/arrangor")
@ClientRegistrationId(AMT_ARRANGOR_CLIENT_ID)
interface AmtArrangorApi {
    @GetExchange("/organisasjonsnummer/{orgnummer}")
    fun hentArrangorByOrgnummer(
        @PathVariable orgnummer: String,
    ): ArrangorMedOverordnetArrangorResponse

    @GetExchange("/{arrangorId}")
    fun hentArrangorById(
        @PathVariable arrangorId: UUID,
    ): ArrangorMedOverordnetArrangorResponse
}
