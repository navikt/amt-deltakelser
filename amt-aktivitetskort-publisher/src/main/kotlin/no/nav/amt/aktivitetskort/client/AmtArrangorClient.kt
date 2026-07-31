package no.nav.amt.aktivitetskort.client

import no.nav.amt.aktivitetskort.domain.Arrangor
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.body
import java.util.UUID

@Service
class AmtArrangorClient(
    @Value($$"${amt.arrangor.url}") baseUrl: String,
    restClientBuilder: RestClient.Builder,
) {
    private val restClient: RestClient = restClientBuilder
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build()

    fun hentArrangor(orgnummer: String): ArrangorMedOverordnetArrangorDto = try {
        restClient
            .get()
            .uri("/api/service/arrangor/organisasjonsnummer/{orgnummer}", orgnummer)
            .retrieve()
            .body<ArrangorMedOverordnetArrangorDto>()
            ?: throw RuntimeException("Tomt svar fra amt-arrangor")
    } catch (e: RestClientResponseException) {
        throw RuntimeException("Kunne ikke hente arrangør med orgnummer $orgnummer fra amt-arrangør. Status=${e.statusCode}", e)
    }

    fun hentArrangor(arrangorId: UUID): ArrangorMedOverordnetArrangorDto = try {
        restClient
            .get()
            .uri("/api/service/arrangor/{arrangorId}", arrangorId)
            .retrieve()
            .body<ArrangorMedOverordnetArrangorDto>()
            ?: throw RuntimeException("Tomt svar fra amt-arrangor")
    } catch (e: RestClientResponseException) {
        throw RuntimeException("Kunne ikke hente arrangør med id $arrangorId fra amt-arrangør. Status=${e.statusCode}", e)
    }

    data class ArrangorMedOverordnetArrangorDto(
        val id: UUID,
        val navn: String,
        val organisasjonsnummer: String,
        val overordnetArrangor: Arrangor?,
    )
}
