package no.nav.amt.aktivitetskort.client

import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange
import java.time.ZonedDateTime
import java.util.UUID

@HttpExchange("/veilarboppfolging")
@ClientRegistrationId("veilarboppfolging")
interface VeilarboppfolgingApi {
    @PostExchange("/api/v3/oppfolging/hent-gjeldende-periode")
    fun hentGjeldendePeriode(
        @RequestBody request: PersonRequest,
    ): ResponseEntity<OppfolgingPeriodeDTO>
}

data class PersonRequest(
    val fnr: String,
)

data class OppfolgingPeriodeDTO(
    val uuid: UUID,
    val startDato: ZonedDateTime,
    val sluttDato: ZonedDateTime?,
)
