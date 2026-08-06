package no.nav.amt.aktivitetskort.client

import no.nav.amt.aktivitetskort.domain.Oppfolgingsperiode
import no.nav.amt.aktivitetskort.utils.toSystemZoneLocalDateTime
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.toEntity
import java.time.ZonedDateTime
import java.util.UUID

@Service
class VeilarboppfolgingClient(
    @Value($$"${veilarboppfolging.url}") baseUrl: String,
    restClientBuilder: RestClient.Builder,
) {
    private val restClient: RestClient = restClientBuilder
        .baseUrl("$baseUrl/veilarboppfolging")
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build()

    fun hentOppfolgingperiode(fnr: String): Oppfolgingsperiode? = try {
        restClient
            .post()
            .uri("/api/v3/oppfolging/hent-gjeldende-periode")
            .body(PersonRequest(fnr))
            .retrieve()
            .toEntity<OppfolgingPeriodeDTO>()
            .body
            ?.toModel()
    } catch (e: RestClientResponseException) {
        throw RuntimeException(
            "Feil ved kall mot veilarboppfolging. Status=${e.statusCode.value()}, body=${e.responseBodyAsString}",
            e,
        )
    }

    private data class PersonRequest(
        val fnr: String,
    )

    data class OppfolgingPeriodeDTO(
        val uuid: UUID,
        val startDato: ZonedDateTime,
        val sluttDato: ZonedDateTime?,
    ) {
        fun toModel() = Oppfolgingsperiode(
            id = uuid,
            startDato = startDato.toSystemZoneLocalDateTime(),
            sluttDato = sluttDato?.toSystemZoneLocalDateTime(),
        )
    }
}
