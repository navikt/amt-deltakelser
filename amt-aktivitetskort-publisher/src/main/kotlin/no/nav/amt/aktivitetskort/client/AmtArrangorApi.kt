package no.nav.amt.aktivitetskort.client

import no.nav.amt.aktivitetskort.domain.Arrangor
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import java.util.UUID

@HttpExchange("/api/service/arrangor")
@ClientRegistrationId("amt-arrangor")
interface AmtArrangorApi {
    @GetExchange("/organisasjonsnummer/{orgnummer}")
    fun hentArrangorByOrgnummer(
        @PathVariable orgnummer: String,
    ): ArrangorMedOverordnetArrangorDto

    @GetExchange("/{arrangorId}")
    fun hentArrangorById(
        @PathVariable arrangorId: UUID,
    ): ArrangorMedOverordnetArrangorDto
}

data class ArrangorMedOverordnetArrangorDto(
    val id: UUID,
    val navn: String,
    val organisasjonsnummer: String,
    val overordnetArrangor: Arrangor?,
)
